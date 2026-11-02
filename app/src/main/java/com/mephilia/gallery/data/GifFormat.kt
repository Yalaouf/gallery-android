package com.mephilia.gallery.data

/** The byte values and limits of the GIF format, shared by [GifEncoder] and [GifDecoder]. */
internal object GifFormat {
    /** Signature of a GIF89a file, the version written (it supports animation and transparency). */
    const val SIGNATURE_89A = "GIF89a"

    /** Signature of an older GIF87a file, which can still be read. */
    const val SIGNATURE_87A = "GIF87a"

    /** Length of the signature at the start of the file. */
    const val SIGNATURE_LENGTH = 6

    /** Largest width or height a GIF can declare (16-bit field). */
    const val MAX_DIMENSION = 0xFFFF

    /** Starts an extension block. */
    const val EXTENSION_INTRODUCER = 0x21

    /** Starts an image (a frame). */
    const val IMAGE_SEPARATOR = 0x2C

    /** Ends the file. */
    const val TRAILER = 0x3B

    /** Label of the graphic control extension: delay, disposal and transparency of the next frame. */
    const val GRAPHIC_CONTROL_LABEL = 0xF9

    /** Size of the graphic control extension's data. */
    const val GRAPHIC_CONTROL_SIZE = 0x04

    /** Label of an application extension, such as the NETSCAPE looping one. */
    const val APPLICATION_LABEL = 0xFF

    /** Size of an application extension's identifier and authentication code. */
    const val APPLICATION_ID_SIZE = 0x0B

    /** Identifier of the application extension telling how many times the animation loops. */
    const val NETSCAPE_LOOP_ID = "NETSCAPE2.0"

    /** Older identifier with the same meaning as [NETSCAPE_LOOP_ID]. */
    const val ANIMEXTS_LOOP_ID = "ANIMEXTS1.0"

    /** Size of the looping sub-block: its id byte and the 16-bit loop count. */
    const val LOOP_SUB_BLOCK_SIZE = 0x03

    /** Id of the looping sub-block. */
    const val LOOP_SUB_BLOCK_ID = 0x01

    /** Loop count meaning "forever". */
    const val LOOP_FOREVER = 0

    /** Ends a sequence of data sub-blocks. */
    const val BLOCK_TERMINATOR = 0x00

    /** Largest data sub-block. */
    const val MAX_SUB_BLOCK_SIZE = 255

    /** Packed-field flag: a color table follows (global in the header, local in an image descriptor). */
    const val COLOR_TABLE_FLAG = 0x80

    /** Packed-field flag of an image descriptor: rows are stored interlaced. */
    const val INTERLACE_FLAG = 0x40

    /** Packed-field mask of the color table size: the table holds 2^(n + 1) colors. */
    const val COLOR_TABLE_SIZE_MASK = 0x07

    /** Packed-field flag of the graphic control extension: the frame has a transparent color. */
    const val TRANSPARENCY_FLAG = 0x01

    /** Position of the disposal method in the graphic control extension's packed field. */
    const val DISPOSAL_SHIFT = 2

    /** Mask of the disposal method, once shifted. */
    const val DISPOSAL_MASK = 0x07

    /** Disposal method: leave the frame in place. */
    const val DISPOSAL_NONE = 0

    /** Disposal method: clear the frame's area to transparent before the next frame. */
    const val DISPOSAL_BACKGROUND = 2

    /** Disposal method: put back what was there before the frame. */
    const val DISPOSAL_PREVIOUS = 3

    /** Most colors in a color table. */
    const val MAX_COLORS = 256

    /** Bytes per color in a color table: red, green, blue. */
    const val BYTES_PER_COLOR = 3

    /** Most codes in the LZW dictionary, which caps the code size at 12 bits. */
    const val MAX_LZW_CODES = 4096

    /** Largest LZW code size, in bits. */
    const val MAX_LZW_CODE_SIZE = 12

    /** Size of an image descriptor's position and size fields, skipped when walking the blocks. */
    const val IMAGE_DESCRIPTOR_POSITION_SIZE = 8

    /** Number of colors in a color table, from the packed field that declares it. */
    fun colorTableSize(packed: Int) = 2 shl (packed and COLOR_TABLE_SIZE_MASK)
}
