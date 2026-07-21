"""
Patch bundletool FileUtils.getPath() to handle ':' in paths on Windows.
Strategy: Modify the bytecode to wrap Paths.get() call in try-catch(InvalidPathException).
On failure, replace ':' with '_' and retry.
"""
import struct
import zipfile
import sys
import os
import shutil

def read_u2(data, offset):
    return struct.unpack('>H', data[offset:offset+2])[0]

def read_u4(data, offset):
    return struct.unpack('>I', data[offset:offset+4])[0]

def write_u2(val):
    return struct.pack('>H', val)

def write_u4(val):
    return struct.pack('>I', val)

def patch_class_file(data):
    """
    Patch the FileUtils class bytecode.
    Adds try-catch around the Paths.get() call in getPath().
    """
    # The getPath method bytecode ends with:
    # 2A       aload_0
    # 03       iconst_0  
    # BD xx xx anewarray String
    # B8 xx xx invokestatic Paths.get
    # B0       areturn
    # 
    # We want to find this pattern and wrap it in try-catch.
    
    # Search for: 2A 03 BD xx xx B8 xx xx B0
    # This is the Paths.get() call sequence
    
    pos = 0
    while pos < len(data) - 10:
        if data[pos] == 0x2A and data[pos+1] == 0x03 and data[pos+2] == 0xBD:
            # Check if followed by B8 xx xx B0
            if pos + 8 < len(data) and data[pos+5] == 0xB8 and data[pos+8] == 0xB0:
                # This looks like the getPath method
                # Verify by checking the preceding bytes
                if pos >= 4 and data[pos-4] == 0x4E:  # astore_0 before aload_0
                    print(f"Found getPath Paths.get call at offset {pos}")
                    
                    # Extract the anewarray and invokestatic constant pool indices
                    anewarray_cp = read_u2(data, pos+3)
                    invokestatic_cp = read_u2(data, pos+6)
                    print(f"  anewarray cp: #{anewarray_cp}, invokestatic cp: #{invokestatic_cp}")
                    
                    # The try block: from pos (aload_0) to pos+8 (after Paths.get)
                    try_start = pos
                    try_end = pos + 8  # After the invokestatic
                    
                    # We need to add a catch block. The new code:
                    # [original: aload_0, iconst_0, anewarray, invokestatic Paths.get, areturn]
                    # catch:
                    #   astore_1        (store exception)
                    #   aload_0         (load original path)
                    #   bipush 58       (':')
                    #   bipush 95       ('_')
                    #   invokevirtual String.replace(char, char)
                    #   astore_0        (store modified path)
                    #   goto try_start  (retry)
                    
                    # First, we need to add String.replace(char,char) to constant pool
                    # Method ref: java/lang/String.replace(CC)Ljava/lang/String;
                    # We need to add:
                    # - Class: java/lang/String (# already exists)
                    # - NameAndType: replace (CC)Ljava/lang/String;
                    # - Methodref: String.replace(char,char)
                    
                    # This is too complex. Let's use a simpler approach:
                    # Instead of String.replace, use a try-catch where we just skip the blame
                    # and return Paths.get("dummy") or Paths.get(".")
                    
                    # Simpler approach: catch InvalidPathException and return Paths.get(".")
                    # catch:
                    #   pop             (discard exception)
                    #   ldc "."         (need "." in constant pool)
                    #   iconst_0
                    #   anewarray String
                    #   invokestatic Paths.get
                    #   areturn
                    
                    # We need to add "." to constant pool. Let's just use a try-catch
                    # that catches the exception and calls Paths.get with a safe path.
                    
                    # Actually, the simplest approach: modify the bytecode to replace
                    # ':' with '_' before calling Paths.get().
                    # We need to add String.replace(char,char) to the constant pool.
                    
                    return add_string_replace(data, pos, try_start, try_end, anewarray_cp, invokestatic_cp)
        
        pos += 1
    
    print("ERROR: Could not find getPath method pattern")
    return None

def add_string_replace(data, pos, try_start, try_end, anewarray_cp, invokestatic_cp):
    """
    Add String.replace(char, char) call before Paths.get() to replace ':' with '_'.
    This is simpler than try-catch but requires adding constant pool entries.
    """
    # This approach is too complex. Let's use try-catch instead.
    return add_try_catch(data, pos, try_start, try_end, anewarray_cp, invokestatic_cp)

def add_try_catch(data, pos, try_start, try_end, anewarray_cp, invokestatic_cp):
    """
    Add try-catch(InvalidPathException) around the Paths.get() call.
    On exception, return a dummy Path instead.
    """
    # We need to:
    # 1. Add InvalidPathException class to constant pool
    # 2. Add exception table entry
    # 3. Add catch block bytecode
    
    # This is getting very complex with raw bytecode modification.
    # Let's try a MUCH simpler approach: just make the Paths.get call
    # use a modified path that doesn't have ':'
    
    # Instead of modifying bytecode, let's just replace the class entirely
    # with a pre-compiled one.
    
    print("Bytecode patching is too complex. Use Java source approach instead.")
    return None

def main():
    jar_path = sys.argv[1] if len(sys.argv) > 1 else "bundletool-original.jar"
    class_entry = "com/android/tools/build/bundletool/model/utils/files/FileUtils.class"
    
    print(f"Patching {jar_path}...")
    
    with zipfile.ZipFile(jar_path, 'r') as zf:
        data = zf.read(class_entry)
    
    print(f"Class size: {len(data)} bytes")
    
    patched = patch_class_file(data)
    
    if patched:
        output = jar_path.replace('.jar', '-patched.jar')
        with zipfile.ZipFile(jar_path, 'r') as zin:
            with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    if item.filename == class_entry:
                        zout.writestr(item, patched)
                    else:
                        zout.writestr(item, zin.read(item.filename))
        print(f"Patched jar: {output}")
        
        # Copy to Gradle cache
        target = jar_path
        shutil.copy2(output, target)
        print(f"Replaced {target}")
    else:
        print("Failed to patch.")

if __name__ == '__main__':
    main()