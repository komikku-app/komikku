#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "glslang::SPIRV" for configuration "Release"
set_property(TARGET glslang::SPIRV APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(glslang::SPIRV PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libSPIRV.a"
  )

list(APPEND _cmake_import_check_targets glslang::SPIRV )
list(APPEND _cmake_import_check_files_for_glslang::SPIRV "${_IMPORT_PREFIX}/lib/libSPIRV.a" )

# Import target "glslang::OSDependent" for configuration "Release"
set_property(TARGET glslang::OSDependent APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(glslang::OSDependent PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libOSDependent.a"
  )

list(APPEND _cmake_import_check_targets glslang::OSDependent )
list(APPEND _cmake_import_check_files_for_glslang::OSDependent "${_IMPORT_PREFIX}/lib/libOSDependent.a" )

# Import target "glslang::glslang" for configuration "Release"
set_property(TARGET glslang::glslang APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(glslang::glslang PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libglslang.a"
  )

list(APPEND _cmake_import_check_targets glslang::glslang )
list(APPEND _cmake_import_check_files_for_glslang::glslang "${_IMPORT_PREFIX}/lib/libglslang.a" )

# Import target "glslang::MachineIndependent" for configuration "Release"
set_property(TARGET glslang::MachineIndependent APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(glslang::MachineIndependent PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libMachineIndependent.a"
  )

list(APPEND _cmake_import_check_targets glslang::MachineIndependent )
list(APPEND _cmake_import_check_files_for_glslang::MachineIndependent "${_IMPORT_PREFIX}/lib/libMachineIndependent.a" )

# Import target "glslang::GenericCodeGen" for configuration "Release"
set_property(TARGET glslang::GenericCodeGen APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(glslang::GenericCodeGen PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libGenericCodeGen.a"
  )

list(APPEND _cmake_import_check_targets glslang::GenericCodeGen )
list(APPEND _cmake_import_check_files_for_glslang::GenericCodeGen "${_IMPORT_PREFIX}/lib/libGenericCodeGen.a" )

# Import target "glslang::glslang-default-resource-limits" for configuration "Release"
set_property(TARGET glslang::glslang-default-resource-limits APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(glslang::glslang-default-resource-limits PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libglslang-default-resource-limits.a"
  )

list(APPEND _cmake_import_check_targets glslang::glslang-default-resource-limits )
list(APPEND _cmake_import_check_files_for_glslang::glslang-default-resource-limits "${_IMPORT_PREFIX}/lib/libglslang-default-resource-limits.a" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
