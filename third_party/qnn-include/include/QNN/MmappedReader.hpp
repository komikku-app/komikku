//==============================================================================
//
//  Copyright (c) 2023 Qualcomm Technologies, Inc.
//  All Rights Reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
#pragma once

#include <algorithm>
#include <memory>
#include <type_traits>
#include <utility>
#include <vector>

#include "MmappedFile.hpp"

namespace mmapped {

class MmappedReader {
 public:
  MmappedReader(std::shared_ptr<BufferLike> mmappedFile, bool littleEndian = true)
      : m_mmappedFile{std::move(mmappedFile)}, m_littleEndian{littleEndian} {}

  // Returns true if the reader is in little endian mode
  bool isLittleEndian() const noexcept { return m_littleEndian; }

  // Seek an offset within the file
  bool seek(int64_t offset);

  // Advance the reader forwards or backwards by a give number of bytes
  bool step(int64_t bytes);

  // Get the size of the file being read
  uint64_t size() const noexcept { return m_mmappedFile ? m_mmappedFile->size() : 0; }

  // Return true if the reader has reached the end of file
  bool atEOF() const noexcept { return m_mmappedFile ? m_offset == m_mmappedFile->size() : true; }

  // Return true if the reader has a valid MmappedFile
  bool isOpen() const noexcept { return m_mmappedFile && m_mmappedFile->operator bool(); }

  // Return true if the failbit is set
  bool fail() const noexcept { return m_failBit; }

  // Return true when the reader has a valid MmappedFile and the failbit is not set
  operator bool() const noexcept { return isOpen() && !fail(); }

  // The current offset of the reader within the file
  uint64_t offset() const noexcept { return m_offset; }

  // Remaining bytes until EOF
  uint64_t remaining() const noexcept {
    return m_mmappedFile ? m_mmappedFile->size() - m_offset : 0;
  }

  // Reinterpret the current data as type T
  template <typename T>
  T* reinterpret() noexcept {
    static_assert(!std::is_pointer<T>::value && !std::is_reference<T>::value,
                  "Cannot parse read pointer types from file.");
    return current<T>();
  }

  // Reinterpret the current data as type T
  template <typename T>
  const T* reinterpret() const noexcept {
    static_assert(!std::is_pointer<T>::value && !std::is_reference<T>::value,
                  "Cannot parse read pointer types from file.");
    return current<const T>();
  }

 private:
  // Shortcut for SFINAE on types that can be read from the file
  template <typename T>
  struct IsPlainScalar : std::is_scalar<typename std::remove_reference<T>::type> {};

 public:
  // Get a value but do not advance the reader. Returns true on success
  template <typename T, typename std::enable_if<IsPlainScalar<T>::value, int>::type = 0>
  bool get(T& dest) {
    if (remaining() < sizeof(T)) {
      m_failBit = true;
      return false;
    }
    m_failBit = false;

    auto data = current<uint8_t>();
    auto dst  = static_cast<void*>(&dest);
    if (endian::isLittle() == m_littleEndian) {
      directRead<sizeof(T)>(data, static_cast<uint8_t*>(dst));
    } else {
      reverseRead<sizeof(T)>(data, static_cast<uint8_t*>(dst));
    }
    return true;
  }

  // Get a value but do not advance the reader
  template <typename T, typename std::enable_if<IsPlainScalar<T>::value, int>::type = 0>
  T get() {
    T toret{};
    (void)get(toret);
    return toret;
  }

  // Get a value and advance the reader. Will return false and set failbit if read failed
  template <typename T, typename std::enable_if<IsPlainScalar<T>::value, int>::type = 0>
  bool read(T& dest) {
    if (!get(dest)) return false;

    step(sizeof(T));
    return true;
  }

  // Read into a string and advance the reader
  bool read(std::string& str, uint64_t size) {
    str.resize(size);
    return read(str);
  }

  // Read into a vector and advance the reader
  template <typename T, typename std::enable_if<IsPlainScalar<T>::value, int>::type = 0>
  bool read(std::vector<T>& vec, uint64_t size) {
    vec.resize(size);
    return read(vec);
  }

  // Copy out a series of bytes. Do not advance the stream
  bool get(uint8_t* dst, uint64_t bytes);

  // Copy out a series of bytes, advancing the stream on success.
  bool read(uint8_t* dst, uint64_t bytes) {
    if (!get(dst, bytes)) return false;
    step(bytes);  // This will succeed if get succeeds
    return true;
  }

  // stream get operator. Read out a value and advance the stream
  template <typename T, typename std::enable_if<IsPlainScalar<T>::value, int>::type = 0>
  MmappedReader& operator>>(T& t) {
    read(t);
    return *this;
  }

  // Get shared ownership of the underlying file
  const std::shared_ptr<BufferLike>& getFile() const noexcept { return m_mmappedFile; }
  /* Disabled this syntax option for now, due to a bug in the violation checker
  private:
    // Allow assignment from function call operator
    // e.g.
    //  MmappedFile mmf("somefile");
    //  MmappedReader<true> reader(mmf);
    //  int x = reader();
    //  float y = reader();
    struct RetRef{
      MmappedReader& owner;
      template<typename T, typename std::enable_if<!std::is_reference<T>::value,int>::type=0>
      operator T() {
        // NOTE: Will just cause UB if no data remains. This could be fixed with T toret{}, but that
  has drawbacks T toret; owner >> toret; return toret;
      }
    };
  public:
    // Read from the stream using inferred type
    RetRef operator()() noexcept{
      return {*this};
    }
  */
 private:
  // Read into a string and advance the reader. Will return false and set failbit if read failed
  bool read(std::string& str) { return read(reinterpret_cast<uint8_t*>(&str[0]), str.size()); }

  // Read into a vector and advance the stream
  template <typename T>
  bool read(std::vector<T>& vec) {
    return read(reinterpret_cast<uint8_t*>(vec.data()), vec.size() * sizeof(T));
  }

  // Get the current data, reinterpreted as a specified type
  template <typename T>
  T* current() noexcept {
    return reinterpret_cast<T*>(m_mmappedFile->data() + m_offset);
  }
  // Get the current data, reinterpreted as a specified type
  template <typename T>
  const T* current() const noexcept {
    return reinterpret<const T*>(m_mmappedFile->data() + m_offset);
  }

  // Read bytes directly from the underlying file
  template <uint64_t Bytes>
  void directRead(const uint8_t* src, uint8_t* dst) const noexcept {
    std::copy_n(src, Bytes, dst);
  }
  // Read bytes in reverse from the underlying file
  template <uint64_t Bytes>
  void reverseRead(const uint8_t* src, uint8_t* dst) const noexcept {
    auto end = dst + Bytes - 1;
    for (uint64_t i = 0; i < Bytes; ++i) {
      end[-static_cast<int64_t>(i)] = src[i];
    }
  }
  // Read bytes directly from the underlying file
  static void directRead(const uint8_t* src, uint64_t bytes, uint8_t* dst) noexcept {
    std::copy_n(src, bytes, dst);
  }
  // Read bytes in reverse from the underlying file
  static void reverseRead(const uint8_t* src, uint64_t bytes, uint8_t* dst) noexcept {
    auto end = dst + bytes - 1;
    for (uint64_t i = 0; i < bytes; ++i) {
      end[-static_cast<int64_t>(i)] = src[i];
    }
  }

  std::shared_ptr<BufferLike> m_mmappedFile;  // The underlying file being read from

  // NOTE: We must not optimize by having a pointer instead of an offset, as mmappedFile can change
  uint64_t m_offset{};  // The current offset within the file
  bool m_failBit{};     // Flag indicating a failure to read
  bool m_littleEndian;  // Flag indicating if the reader is in little endian mode
};

}  // namespace mmapped
