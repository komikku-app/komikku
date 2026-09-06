#include "anime4k.h"
#include <algorithm>
#include <sstream>

const char *VERTEX_SHADER_SOURCE = R"glsl(
    #version 300 es
    layout(location = 0) in vec2 aPos;
    layout(location = 1) in vec2 aTexCoord;
    out vec2 vTexCoord;
    void main() {
        gl_Position = vec4(aPos, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)glsl";

Anime4K::Anime4K()
    : display(EGL_NO_DISPLAY), context(EGL_NO_CONTEXT), surface(EGL_NO_SURFACE),
      quad_vbo(0), quad_vao(0), initialized(false) {}

Anime4K::~Anime4K() { term_egl(); }

bool Anime4K::init_egl() {
  if (initialized)
    return true;

  display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  eglInitialize(display, nullptr, nullptr);

  EGLint configAttribs[] = {EGL_RENDERABLE_TYPE,
                            EGL_OPENGL_ES3_BIT,
                            EGL_SURFACE_TYPE,
                            EGL_PBUFFER_BIT,
                            EGL_RED_SIZE,
                            8,
                            EGL_GREEN_SIZE,
                            8,
                            EGL_BLUE_SIZE,
                            8,
                            EGL_ALPHA_SIZE,
                            8,
                            EGL_NONE};
  EGLConfig config;
  EGLint numConfigs;
  eglChooseConfig(display, configAttribs, &config, 1, &numConfigs);

  EGLint pbufferAttribs[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
  surface = eglCreatePbufferSurface(display, config, pbufferAttribs);

  EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
  context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);

  if (!eglMakeCurrent(display, surface, surface, context)) {
    ANIME4K_LOGE("Failed to make EGL context current");
    return false;
  }

  setup_quad();
  initialized = true;
  return true;
}

void Anime4K::term_egl() {
  if (!initialized)
    return;
  for (auto &pass : passes)
    glDeleteProgram(pass.program);
  for (auto &it : textures)
    glDeleteTextures(1, &it.second);
  if (quad_vbo)
    glDeleteBuffers(1, &quad_vbo);
  if (quad_vao)
    glDeleteVertexArrays(1, &quad_vao);
  eglDestroyContext(display, context);
  eglDestroySurface(display, surface);
  eglTerminate(display);
  initialized = false;
}

void Anime4K::setup_quad() {
  float vertices[] = {
      -1.0f, 1.0f, 0.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f,
      1.0f,  1.0f, 1.0f, 1.0f, 1.0f,  -1.0f, 1.0f, 0.0f,
  };
  glGenVertexArrays(1, &quad_vao);
  glGenBuffers(1, &quad_vbo);
  glBindVertexArray(quad_vao);
  glBindBuffer(GL_ARRAY_BUFFER, quad_vbo);
  glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
  glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *)0);
  glEnableVertexAttribArray(0);
  glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float),
                        (void *)(2 * sizeof(float)));
  glEnableVertexAttribArray(1);
}

GLuint Anime4K::compile_program(const std::string &name,
                                const std::string &source) {
  auto compile = [](GLenum type, const char *src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
      char log[512];
      glGetShaderInfoLog(shader, 512, nullptr, log);
      ANIME4K_LOGE("Shader compile error: %s", log);
    }
    return shader;
  };

  GLuint vs = compile(GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE);
  GLuint fs = compile(GL_FRAGMENT_SHADER, source.c_str());
  GLuint program = glCreateProgram();
  glAttachShader(program, vs);
  glAttachShader(program, fs);
  glLinkProgram(program);
  glDeleteShader(vs);
  glDeleteShader(fs);
  return program;
}

int Anime4K::load(const std::vector<std::string> &shaders,
                  const std::vector<std::string> &shader_names) {
  if (!init_egl())
    return -1;

  for (size_t i = 0; i < shaders.size(); ++i) {
    const std::string &src = shaders[i];
    std::stringstream ss(src);
    std::string line;
    Pass pass;
    pass.scale_x = 1.0f;
    pass.scale_y = 1.0f;

    std::string fragment_body;
    std::vector<std::string> bindings;

    while (std::getline(ss, line)) {
      if (line.find("//!DESC") == 0)
        pass.desc = line.substr(8);
      if (line.find("//!BIND") == 0)
        bindings.push_back(line.substr(8));
      if (line.find("//!SAVE") == 0)
        pass.save_target = line.substr(8);
      if (line.find("//!WIDTH") == 0 && line.find("*") != std::string::npos)
        pass.scale_x = 2.0f;
      if (line.find("//!HEIGHT") == 0 && line.find("*") != std::string::npos)
        pass.scale_y = 2.0f;
      if (line.find("//!") != 0)
        fragment_body += line + "\n";
    }

    std::string full_fs = "#version 300 es\nprecision highp float;\nin vec2 "
                          "vTexCoord;\nout vec4 fragColor;\n";
    for (const auto &b : bindings) {
      full_fs += "uniform sampler2D " + b + "_tex;\n";
      full_fs += "uniform vec2 " + b + "_size;\n";
      full_fs += "#define " + b + "_tex(pos) texture(" + b + "_tex, pos)\n";
      full_fs += "#define " + b + "_texOff(off) texture(" + b +
                 "_tex, vTexCoord + off / " + b + "_size)\n";
      full_fs += "#define " + b + "_pos vTexCoord\n";
    }
    full_fs += fragment_body;
    full_fs += "\nvoid main() { fragColor = hook(); }\n";

    pass.program = compile_program(shader_names[i], full_fs);
    pass.bind_targets = bindings;
    passes.push_back(pass);
    ANIME4K_LOGD("Loaded pass: %s -> %s (Scale %.1fx)", pass.desc.c_str(),
                 pass.save_target.c_str(), pass.scale_x);
  }
  return 0;
}

int Anime4K::process(int width, int height, unsigned char *pixels, int &out_w,
                     int &out_h, unsigned char *out_pixels) {
  if (!init_egl())
    return -1;

  auto get_tex = [&](const std::string &name, int w, int h) {
    if (textures.count(name)) {
      if (tex_sizes[name].first == w && tex_sizes[name].second == h)
        return textures[name];
      glDeleteTextures(1, &textures[name]);
    }
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                 nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    textures[name] = tex;
    tex_sizes[name] = {w, h};
    return tex;
  };

  // Upload initial image
  GLuint main_tex = get_tex("MAIN", width, height);
  glBindTexture(GL_TEXTURE_2D, main_tex);
  glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA,
                  GL_UNSIGNED_BYTE, pixels);

  GLuint fbo;
  glGenFramebuffers(1, &fbo);
  glBindFramebuffer(GL_FRAMEBUFFER, fbo);

  int curr_w = width, curr_h = height;

  for (const auto &pass : passes) {
    int next_w = curr_w * pass.scale_x;
    int next_h = curr_h * pass.scale_y;
    GLuint out_tex = get_tex(pass.save_target, next_w, next_h);

    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                           out_tex, 0);
    glViewport(0, 0, next_w, next_h);
    glUseProgram(pass.program);

    for (size_t j = 0; j < pass.bind_targets.size(); ++j) {
      const std::string &bname = pass.bind_targets[j];
      glActiveTexture(GL_TEXTURE0 + j);
      glBindTexture(GL_TEXTURE_2D, textures[bname]);
      glUniform1i(glGetUniformLocation(pass.program, (bname + "_tex").c_str()),
                  (int)j);
      glUniform2f(glGetUniformLocation(pass.program, (bname + "_size").c_str()),
                  (float)tex_sizes[bname].first,
                  (float)tex_sizes[bname].second);
    }

    glBindVertexArray(quad_vao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    curr_w = next_w;
    curr_h = next_h;
  }

  out_w = curr_w;
  out_h = curr_h;
  // Read back final result
  glReadPixels(0, 0, curr_w, curr_h, GL_RGBA, GL_UNSIGNED_BYTE, out_pixels);

  glDeleteFramebuffers(1, &fbo);
  return 0;
}

void Anime4K::get_output_size(int width, int height, int &out_w, int &out_h) {
  float curr_w = width, curr_h = height;
  for (const auto &pass : passes) {
    curr_w *= pass.scale_x;
    curr_h *= pass.scale_y;
  }
  out_w = (int)curr_w;
  out_h = (int)curr_h;
}
