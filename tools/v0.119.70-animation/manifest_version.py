from struct import pack_into, unpack_from


VERSION_CODE = 203
VERSION_NAME = "0.119.70-fork-compact-music"


def stamp_manifest_version(manifest):
    result = bytearray(manifest)
    kind, offset, size = unpack_from("<HHI", result)
    if kind != 3 or size != len(result):
        raise ValueError("Expected Android's binary XML manifest")

    pool = None
    while offset < size:
        kind, head, length = unpack_from("<HHI", result, offset)
        if length < head or head < 8 or offset + length > size:
            raise ValueError("Invalid manifest chunk")
        if kind == 1:
            pool = (offset, head)
        if kind == 0x102:
            break
        offset += length
    else:
        raise ValueError("Manifest root element is missing")

    if pool is None:
        raise ValueError("Manifest string pool is missing")
    pool_offset, pool_header = pool
    count, _, flags, strings = unpack_from("<4I", result, pool_offset + 8)
    if flags & 0x100:
        raise ValueError("Expected the original UTF-16 manifest string pool")

    def read_string(index):
        if index >= count:
            raise ValueError("Invalid manifest string index")
        relative, = unpack_from("<I", result, pool_offset + pool_header + index * 4)
        start = pool_offset + strings + relative
        units, = unpack_from("<H", result, start)
        if units & 0x8000:
            raise ValueError("Unexpected long manifest string")
        end = start + 2 + units * 2
        return start, end, result[start + 2:end].decode("utf-16le")

    element = offset + head
    _, name, attr_start, attr_size, attr_count = unpack_from("<2I3H", result, element)
    if read_string(name)[2] != "manifest" or attr_size != 20:
        raise ValueError("Unexpected manifest root layout")
    updated = set()
    for index in range(attr_count):
        attr = element + attr_start + index * attr_size
        _, name, raw, _, _, value_type, value = unpack_from("<3IHBBI", result, attr)
        key = read_string(name)[2]
        if key == "versionCode" and value_type == 0x10 and value == 201:
            pack_into("<I", result, attr + 16, VERSION_CODE)
            updated.add(key)
        if key == "versionName" and value_type == 0x03 and raw == value:
            start, end, original = read_string(value)
            encoded = VERSION_NAME.encode("utf-16le")
            if original != "0.119.70-stack-icon-clip-advert-experiment" or len(encoded) > end - start - 2:
                raise ValueError("Unexpected manifest version string")
            pack_into("<H", result, start, len(encoded) // 2)
            result[start + 2:end + 2] = encoded.ljust(end - start, b"\x00")
            updated.add(key)
    if updated != {"versionCode", "versionName"}:
        raise ValueError("Expected both original version attributes")
    return bytes(result)
