//==============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
//  All rights reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//==============================================================================
#pragma once

// NOTE: Write is not supported at all for windows. This could be added in the future if needed

#ifdef _MSC_VER
#include <windows.h>
#endif

#include <cstddef>
#include <cstdint>
#include <string>
#include <utility>

namespace mmapped {

// Helper to get current endianness
namespace endian {
inline bool isLittle() noexcept {
  int n{1};
  return *reinterpret_cast<const uint8_t*>(&n);
}

inline bool isBig() noexcept { return !isLittle(); }
}  // namespace endian

// A base class for anything that can behave like a plain buffer
class BufferLike {
 public:
  virtual ~BufferLike() = default;

  // Return true if the buffer is valid and readable
  virtual operator bool() const noexcept = 0;

  // Return the size of the buffer
  virtual uint64_t size() const noexcept = 0;

  // Return a pointer to the start of the buffer
  virtual const uint8_t* data() const noexcept = 0;

  // Return a pointer to the start of the buffer
  virtual uint8_t* data() = 0;

  // Return the name of the buffer (or empty string for plain buffers)
  virtual const std::string& filename() const = 0;
};

// A plain data buffer
class DataBuffer : public BufferLike {
 public:
  DataBuffer(const uint8_t* data, uint64_t size) noexcept : m_data{data}, m_size{size} {}

  DataBuffer(const DataBuffer&) = delete;
  DataBuffer& operator=(const DataBuffer&) = delete;
  DataBuffer(const DataBuffer&&) = delete;
  DataBuffer& operator=(const DataBuffer&&) = delete;
  ~DataBuffer() override = default;

  operator bool() const noexcept override { return m_data && m_size; }

  uint64_t size() const noexcept override { return m_size; }

  const uint8_t* data() const noexcept override { return m_data; }

  uint8_t* data() override {
    // TODO: Get rid of this const cast if we never need write support for Zips
    return const_cast<uint8_t*>(m_data);
  }

  const std::string& filename() const override {
    static std::string s_bufferFileName("");
    return s_bufferFileName;
  }

 private:
  const uint8_t* m_data;
  uint64_t m_size;
};

// A memory mapped file
class File : public BufferLike {
 public:
  // Given a filename, open and map the file into memory
  explicit File(std::string filename, bool readwrite = false);

  File() noexcept = default;

  ~File();

  // Copy Ctor and copy assignment are explicitly deleted.
  File(const File&) = delete;
  File(File&& other) noexcept;

  File& operator=(const File&) = delete;
  File& operator               =(File&& other) noexcept;

  // Returns the name of the mmapped file
  const std::string& filename() const override;

  // Returns true if the file is open and readable
  bool isOpen() const noexcept;
  // Returns true if the file is open and readable
  explicit operator bool() const noexcept override;

  // Returns the size of the file
  uint64_t size() const noexcept override;

  // Returns true if the file is writeable
  bool isWritable() const noexcept;

  // Returns a pointer to the beginning of the file
  uint8_t* data() override;
  // Returns a pointer to the beginning of the file
  const uint8_t* data() const noexcept override;

  // Swap one file object with another, exchanging the underlying file
  void swap(File& other);

  // NOTE: Not needed for read-only files
  // Shrink or grow the underlying file
  bool resize(uint64_t newSize);
  // Remap the file with a different readwrite option (true for write support)
  bool remap(bool readwrite);

  // Unmap and close the underlying file
  bool close();

  //  Not yet implemented for windows
  // TODO: Come up with a generic set of advice types to allow advice on Windows
  // Give the kernel advice for a specific range of memory
  bool adviseRange(uint64_t offset, uint64_t length, int advice);
  // Tell the kernel that a specified range of memory can be marked as a candidate to be dropped
  bool freeRange(uint64_t offset, uint64_t length);

 private:
#ifndef _MSC_VER
  // Given a range of addresses, find the page page boundary aligned range
  std::pair<void*, uint64_t> getRange(uint64_t offset, uint64_t length);
  // Return the OS's page size. Required for giving advice
  static uint64_t getPageSize();

  // Open the file, but do not map it
  std::pair<int, uint64_t> openFileInternal(const char* filename, int openflags, int modeFlags);
  // Map the file into memory
  std::pair<void*, uint64_t> mapFileInternal(uint64_t size, bool readwrite);
  // Remap the file with a different size, allowing readwrite files to grow or shrink
  std::pair<void*, uint64_t> remapInternal(uint64_t size);

  // Open and map the file into memory
  bool open(bool readwrite, uint64_t overrideSize = 0);

  // Unmap the file from memory
  bool unmapInternal();
  // Close the underlying file
  bool closeInternal();
  // Clear out the data members
  void resetMembers();

  std::string m_filename;
  int m_fileDescriptor{-1};  // FD of the underlying file
  void* m_address{nullptr};  // Location of the mapped file in memory
  uint64_t m_size{0};        // Size of the mapped file
  bool m_readWrite{false};   // True if the file is writeable
#else                        // ndef _MSC_VER

  bool open();

  void resetMembers();

  std::string m_filename;
  HANDLE m_fileHandle{INVALID_HANDLE_VALUE};
  HANDLE m_mappingHandle{NULL};
  LPVOID m_mappingPtr{};
  uint64_t m_size{};
#endif                       // ndef _MSC_VER
};

}  // namespace mmapped
