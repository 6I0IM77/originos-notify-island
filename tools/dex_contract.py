"""Read DEX definitions without loading or executing the vendor APK."""
import struct
import zipfile

definitions = {}
field_definitions = {}

def read_dex(data):
    def u4(offset):
        return struct.unpack_from('<I', data, offset)[0]
    def uleb(offset):
        value = shift = 0
        while True:
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, offset
            shift += 7
    strings = []
    for i in range(u4(56)):
        start = u4(u4(60) + 4 * i)
        _, start = uleb(start)
        strings.append(data[start:data.index(b'\0', start)].decode('utf-8', errors='replace'))
    types = [strings[u4(u4(68) + 4*i)] for i in range(u4(64))]
    protos = []
    for i in range(u4(72)):
        off = u4(76) + i*12
        params = u4(off + 8)
        args = [] if not params else [types[struct.unpack_from('<H', data, params + 4 + j*2)[0]] for j in range(u4(params))]
        protos.append((''.join(args), types[u4(off + 4)]))
    methods = []
    for i in range(u4(88)):
        owner, proto, name = struct.unpack_from('<HHI', data, u4(92) + i*8)
        methods.append((types[owner], strings[name], *protos[proto]))
    fields = []
    for i in range(u4(80)):
        owner, field_type, name = struct.unpack_from('<HHI', data, u4(84) + i*8)
        fields.append((types[owner], strings[name], types[field_type]))
    for i in range(u4(96)):
        off = u4(100) + 32*i
        owner = types[u4(off)]
        class_data = u4(off + 24)
        if not class_data:
            continue
        sizes = []
        for _ in range(4):
            size, class_data = uleb(class_data)
            sizes.append(size)
        declared_fields = field_definitions.setdefault(owner, {})
        for count in sizes[:2]:
            index = 0
            for _ in range(count):
                diff, class_data = uleb(class_data)
                flags, class_data = uleb(class_data)
                index += diff
                _, name, field_type = fields[index]
                declared_fields[name] = (flags, field_type)
        defined = definitions.setdefault(owner, {})
        for count in sizes[2:]:
            index = 0
            for _ in range(count):
                diff, class_data = uleb(class_data)
                flags, class_data = uleb(class_data)
                _, class_data = uleb(class_data)
                index += diff
                _, name, args, result = methods[index]
                defined[name, args] = (flags, result)

