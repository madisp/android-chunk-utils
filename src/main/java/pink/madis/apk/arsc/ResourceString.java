/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pink.madis.apk.arsc;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.UnsignedBytes;

import java.nio.ByteBuffer;

/** Provides utilities to decode/encode a String packed in an arsc resource file. */
public final class ResourceString {

  /** Type of {@link ResourceString} to encode / decode. */
  public enum Type {
    UTF8, UTF16
  }

  private ResourceString() {} // Private constructor

  /**
   * Given a buffer and an offset into the buffer, returns a String. The {@code offset} is the
   * 0-based byte offset from the start of the buffer where the string resides. How this data is
   * interpreted depends on the string's {@code type}.
   *
   * <p>Here's an example UTF-8-encoded string of ab©:
   * <pre>
   * 03 04 61 62 C2 A9 00
   * </pre>
   *
   * @param buffer The buffer containing the string to decode.
   * @param offset Offset into the buffer where the string resides.
   * @param type The encoding type that the {@link ResourceString} is encoded in.
   * @return The decoded string.
   */
  public static String decodeString(ByteBuffer buffer, int offset, Type type) {
    // Both UTF-8 and UTF-16 strings begin with the length in UTF-16 code units (= 2-byte units).
    // This is ignored when decoding a UTF-8 string, but we need to read it anyway to adjust our
    // offset.
    //
    // See: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/androidfw/StringPool.cpp;l=364-427;drc=1d6d8ac9feb221f47692250647269f3753bdee60
    // See: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/androidfw/ResourceTypes.cpp;l=952-957;drc=61197364367c9e404c7da6900658f1b16c42d0da
    int utf16CodeUnits = decodeLength(buffer, offset, type);
    offset += computeLengthOffset(utf16CodeUnits, type);
    if (type == Type.UTF8) {
      // For a UTF-8 string the next value is the length in UTF-8 code units (= 1-byte units).
      int utf8CodeUnits = decodeLength(buffer, offset, type);
      offset += computeLengthOffset(utf8CodeUnits, type);

      // Strings in .arsc files are encoded as modified UTF-8, not regular UTF-8, so we need to
      // convert between them.
      //
      // See: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/androidfw/Util.cpp;l=210-215;drc=a577514789fc241abe15f793a66f19d6431f7769
      // See: https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/io/DataInput.html#modified-utf-8
      byte[] utf8 = modifiedUtf8ToUtf8(buffer, offset, utf8CodeUnits);
      return new String(utf8, UTF_8);
    } else {
      int lengthBytes = utf16CodeUnits * 2;
      return new String(buffer.array(), offset, lengthBytes, UTF_16LE);
    }
  }

  /**
   * Encodes a string in either UTF-8 or UTF-16 and returns the bytes of the encoded string.
   *
   * <p>Here's an example UTF-8-encoded string of ab©:
   * <pre>03 04 61 62 C2 A9 00</pre>
   *
   * @param str The string to be encoded.
   * @param type The encoding type that the {@link ResourceString} should be encoded in.
   * @return The encoded string.
   */
  public static byte[] encodeString(String str, Type type) {
    byte[] bytes;
    if (type == Type.UTF8) {
      // Strings in .arsc files are encoded as modified UTF-8, not regular UTF-8, so we need to
      // convert between them.
      //
      // See: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/androidfw/StringPool.cpp;l=367;drc=1d6d8ac9feb221f47692250647269f3753bdee60
      // See: https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/io/DataInput.html#modified-utf-8
      bytes = utf8ToModifiedUtf8(str.getBytes(UTF_8));
    } else {
      bytes = str.getBytes(UTF_16LE);
    }

    // +5 bytes is for the length(s) (2+2 bytes for UTF-8, 4 bytes for UTF-16) and a null
    // terminator.
    ByteArrayDataOutput output = ByteStreams.newDataOutput(bytes.length + 5);

    // Both UTF-8 and UTF-16 strings begin with the length in UTF-16 code units (= 2-byte units),
    // which is what String.length() returns.
    //
    // See: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/androidfw/StringPool.cpp;l=364-427;drc=1d6d8ac9feb221f47692250647269f3753bdee60
    // See: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/androidfw/ResourceTypes.cpp;l=952-957;drc=61197364367c9e404c7da6900658f1b16c42d0da
    int utf16CodeUnits = str.length();
    encodeLength(output, utf16CodeUnits, type);
    if (type == Type.UTF8) {
      // For a UTF-8 string the next value is the length in UTF-8 code units (= 1-byte units).
      encodeLength(output, bytes.length, type);
    }
    // Next is the string's bytes.
    output.write(bytes);
    // Then finally a null terminator.
    if (type == Type.UTF8) {
      output.write(0);
    } else {
      output.writeShort(0);
    }
    return output.toByteArray();
  }

  private static void encodeLength(ByteArrayDataOutput output, int length, Type type) {
    if (length < 0) {
      output.write(0);
      return;
    }
    if (type == Type.UTF8) {
      if (length > 0x7F) {
        output.write(((length & 0x7F00) >> 8) | 0x80);
      }
      output.write(length & 0xFF);
    } else {  // UTF-16
      // TODO(acornwall): Replace output with a little-endian output.
      if (length > 0x7FFF) {
        int highBytes = ((length & 0x7FFF0000) >> 16) | 0x8000;
        output.write(highBytes & 0xFF);
        output.write((highBytes & 0xFF00) >> 8);
      }
      int lowBytes = length & 0xFFFF;
      output.write(lowBytes & 0xFF);
      output.write((lowBytes & 0xFF00) >> 8);
    }
  }

  private static int computeLengthOffset(int length, Type type) {
    return (type == Type.UTF8 ? 1 : 2) * (length >= (type == Type.UTF8 ? 0x80 : 0x8000) ? 2 : 1);
  }

  private static int decodeLength(ByteBuffer buffer, int offset, Type type) {
    return type == Type.UTF8 ? decodeLengthUTF8(buffer, offset) : decodeLengthUTF16(buffer, offset);
  }

  private static int decodeLengthUTF8(ByteBuffer buffer, int offset) {
    // UTF-8 strings use a clever variant of the 7-bit integer for packing the string length.
    // If the first byte is >= 0x80, then a second byte follows. For these values, the length
    // is WORD-length in big-endian & 0x7FFF.
    int length = UnsignedBytes.toInt(buffer.get(offset));
    if ((length & 0x80) != 0) {
      length = ((length & 0x7F) << 8) | UnsignedBytes.toInt(buffer.get(offset + 1));
    }
    return length;
  }

  private static int decodeLengthUTF16(ByteBuffer buffer, int offset) {
    // UTF-16 strings use a clever variant of the 7-bit integer for packing the string length.
    // If the first word is >= 0x8000, then a second word follows. For these values, the length
    // is DWORD-length in big-endian & 0x7FFFFFFF.
    int length = (buffer.getShort(offset) & 0xFFFF);
    if ((length & 0x8000) != 0) {
      length = ((length & 0x7FFF) << 16) | (buffer.getShort(offset + 2) & 0xFFFF);
    }
    return length;
  }

  // Converts modified UTF-8 to standard UTF-8. Modified UTF-8 differs from standard UTF-8 in two
  // ways:
  //
  // 1. 4-byte sequences are not used. Instead, supplementary characters (code points above U+FFFF,
  //    outside the BMP) are encoded as a 3-byte surrogate pair.
  // 2. null (U+0000) is encoded as the 2-byte sequence 0xC080 instead of the 1-byte sequence 0x00.
  //
  // All other characters use the same encoding in both formats.
  //
  // Based on fbjni's modifiedUTF8ToUTF8.
  //
  // See: https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/io/DataInput.html#modified-utf-8
  // See: https://github.com/facebookincubator/fbjni/blob/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L172
  private static byte[] modifiedUtf8ToUtf8(ByteBuffer modifiedUtf8, int offset, int len) {
    // Modified UTF-8 is never shorter than the equivalent UTF-8 (surrogate pairs shrink from 6
    // bytes to 4 bytes, nulls shrink from 2 bytes to 1 byte), so this buffer will always be big
    // enough.
    byte[] utf8 = new byte[len];
    int modifiedIndex = 0;
    int utf8Index = 0;

    while (modifiedIndex < len) {
      if (len >= modifiedIndex + 6
          && (modifiedUtf8.get(offset + modifiedIndex) & 0xFF) == 0xED
          && ((modifiedUtf8.get(offset + modifiedIndex + 1) & 0xFF) & 0xF0) == 0xA0
          && (modifiedUtf8.get(offset + modifiedIndex + 3) & 0xFF) == 0xED
          && ((modifiedUtf8.get(offset + modifiedIndex + 4) & 0xFF) & 0xF0) == 0xB0) {
        // Supplementary characters encoded as a 3-byte surrogate pair become a 4-byte sequence.
        int highSurrogate = decode3ByteUtf8(modifiedUtf8, offset + modifiedIndex);
        int lowSurrogate = decode3ByteUtf8(modifiedUtf8, offset + modifiedIndex + 3);
        int codePoint = 0x10000 + (((highSurrogate & 0x3FF) << 10) | (lowSurrogate & 0x3FF));
        encode4ByteUtf8(codePoint, utf8, utf8Index);
        modifiedIndex += 6;
        utf8Index += 4;
      } else if (len >= modifiedIndex + 2
          && (modifiedUtf8.get(offset + modifiedIndex) & 0xFF) == 0xC0
          && (modifiedUtf8.get(offset + modifiedIndex + 1) & 0xFF) == 0x80) {
        // Nulls (U+0000) encoded as a 2-byte sequence become a 1-byte sequence.
        utf8[utf8Index] = 0;
        modifiedIndex += 2;
        utf8Index++;
      } else {
        // Everything else is unchanged.
        utf8[utf8Index] = modifiedUtf8.get(offset + modifiedIndex);
        modifiedIndex++;
        utf8Index++;
      }
    }

    byte[] result = new byte[utf8Index];
    System.arraycopy(utf8, 0, result, 0, utf8Index);
    return result;
  }

  // See: https://github.com/facebookincubator/fbjni/blame/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L42
  private static int decode3ByteUtf8(ByteBuffer in, int offset) {
    return ((in.get(offset) & 0x0F) << 12)
        | ((in.get(offset + 1) & 0x3F) << 6)
        | (in.get(offset + 2) & 0x3F);
  }

  // See: https://github.com/facebookincubator/fbjni/blame/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L46
  private static void encode4ByteUtf8(int codePoint, byte[] out, int offset) {
    out[offset] = (byte) (0xF0 | (codePoint >> 18));
    out[offset + 1] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
    out[offset + 2] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
    out[offset + 3] = (byte) (0x80 | (codePoint & 0x3F));
  }

  // Converts standard UTF-8 to modified UTF-8. Modified UTF-8 differs from standard UTF-8 in two
  // ways:
  //
  // 1. 4-byte sequences are not used. Instead, supplementary characters (code points above U+FFFF,
  //    outside the BMP) are encoded as a 3-byte surrogate pair.
  // 2. null (U+0000) is encoded as 0xC080 (2 bytes) instead of 0x00 (1 byte).
  //
  // All other characters use the same encoding in both formats.
  //
  // Based on fbjni's utf8ToModifiedUTF8.
  //
  // See: https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/io/DataInput.html#modified-utf-8
  // See: https://github.com/facebookincubator/fbjni/blob/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L106
  private static byte[] utf8ToModifiedUtf8(byte[] utf8) {
    byte[] modified = new byte[modifiedUtf8Length(utf8)];
    int utf8Index = 0;
    int modifiedIndex = 0;

    while (utf8Index < utf8.length) {
      if (utf8Index + 4 <= utf8.length && isFourByteUtf8Encoding(utf8[utf8Index])) {
        // Supplementary characters encoded as a 4-byte sequence become a 3-byte surrogate pair.
        int codePoint = ((utf8[utf8Index] & 0x07) << 18)
            | ((utf8[utf8Index + 1] & 0x3F) << 12)
            | ((utf8[utf8Index + 2] & 0x3F) << 6)
            | (utf8[utf8Index + 3] & 0x3F);
        int highSurrogate = ((codePoint - 0x10000) >> 10) | 0xD800;
        int lowSurrogate = ((codePoint - 0x10000) & 0x3FF) | 0xDC00;
        encode3ByteUtf8(highSurrogate, modified, modifiedIndex);
        encode3ByteUtf8(lowSurrogate, modified, modifiedIndex + 3);
        utf8Index += 4;
        modifiedIndex += 6;
      } else if (utf8[utf8Index] == 0) {
        // Nulls (U+0000) encoded as a 1-byte sequence become a 2-byte sequence.
        modified[modifiedIndex] = (byte) 0xC0;
        modified[modifiedIndex + 1] = (byte) 0x80;
        utf8Index++;
        modifiedIndex += 2;
      } else {
        // Everything else is unchanged.
        modified[modifiedIndex] = utf8[utf8Index];
        utf8Index++;
        modifiedIndex++;
      }
    }

    return modified;
  }

  // See: https://github.com/facebookincubator/fbjni/blob/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L61
  private static int modifiedUtf8Length(byte[] utf8) {
    int modifiedUtf8Length = 0;
    int index = 0;
    while (index < utf8.length) {
      if (index + 4 <= utf8.length && isFourByteUtf8Encoding(utf8[index])) {
        // 4-byte sequences expand from 4 to 6 bytes.
        modifiedUtf8Length += 6;
        index += 4;
      } else if (utf8[index] == 0) {
        // Null (U+0000) expands from 1 to 2 bytes.
        modifiedUtf8Length += 2;
        index += 1;
      } else {
        // Everything else stays the same size.
        modifiedUtf8Length += 1;
        index += 1;
      }
    }
    return modifiedUtf8Length;
  }

  // See: https://github.com/facebookincubator/fbjni/blob/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L58
  private static boolean isFourByteUtf8Encoding(byte b) {
    return (b & 0xF8) == 0xF0;
  }

  // See: https://github.com/facebookincubator/fbjni/blob/caacce89ac0c494034e8c36fd0ab0d6fce951785/cxx/fbjni/detail/utf8.cpp#L32
  private static void encode3ByteUtf8(int codePoint, byte[] out, int offset) {
    out[offset] = (byte) (0xE0 | (codePoint >> 12));
    out[offset + 1] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
    out[offset + 2] = (byte) (0x80 | (codePoint & 0x3F));
  }
}
