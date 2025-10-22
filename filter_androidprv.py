#!/usr/bin/env python3
import os
import shutil
import sys

if len(sys.argv) < 3:
    print("Usage: filter_androidprv.py <output_dir> <res_dir1> [<res_dir2> ...]")
    sys.exit(1)

DEST_DIR = sys.argv[1]
SOURCE_DIRS = sys.argv[2:]

def process_file(src_path, dest_path):
    with open(src_path, "r", encoding="utf-8") as f:
        content = f.read()

    modified = False

    # Specific replacements
    if '?androidprv:attr/textColorOnAccent' in content:
        content = content.replace('?androidprv:attr/textColorOnAccent', '?attr/colorOnSecondaryContainer')
        modified = True

    if '?androidprv:attr/colorAccentPrimaryVariant' in content:
        content = content.replace('?androidprv:attr/colorAccentPrimaryVariant', '?attr/colorOnPrimaryContainer')
        modified = True

    if '?androidprv:attr/colorAccentPrimary' in content:
        content = content.replace('?androidprv:attr/colorAccentPrimary', '?attr/colorOnPrimary')
        modified = True

    if 'fontFamily="@*android:string/config_bodyFontFamilyMedium"' in content:
        content = content.replace('fontFamily="@*android:string/config_bodyFontFamilyMedium"', 'textFontWeight="@integer/bodyFontFamilyMediumWeight"')
        modified = True

    if 'fontFamily="@*android:string/config_bodyFontFamily"' in content:
        content = content.replace('fontFamily="@*android:string/config_bodyFontFamily"', 'textFontWeight="@integer/bodyFontFamilyWeight"')
        modified = True

    # General androidprv: removal
    if 'androidprv:' in content:
        content = content.replace('androidprv:', '')
        modified = True

    if modified:
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        with open(dest_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Modified: {src_path}")
        return True

    return False

def main():
    print("=== AndroidPrv Namespace Filter ===")
    print(f"Output Dir: {DEST_DIR}")

    if os.path.exists(DEST_DIR):
        shutil.rmtree(DEST_DIR, ignore_errors=True)

    files_processed = 0
    files_modified = 0

    for source_dir in SOURCE_DIRS:
        if not os.path.exists(source_dir):
            continue

        for root, _, files in os.walk(source_dir):
            for file in files:
                if file.endswith(".xml"):
                    src_path = os.path.join(root, file)
                    rel_path = os.path.relpath(src_path, source_dir)
                    dest_path = os.path.join(DEST_DIR, rel_path)
                    files_processed += 1
                    if process_file(src_path, dest_path):
                        files_modified += 1

    print(f"Files scanned   : {files_processed}")
    print(f"Files modified  : {files_modified}")
    if files_modified > 0:
        print("Filtering completed successfully.")
    else:
        print("No files modified.")

if __name__ == "__main__":
    main()
