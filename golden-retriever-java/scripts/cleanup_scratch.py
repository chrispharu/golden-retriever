#!/usr/bin/env python3
import os
import sys
import argparse
from pathlib import Path

# List of critical code extensions that must NEVER be deleted directly
# Unless they are marked as a backup (e.g., .java.bak, .ts.tmp)
CRITICAL_EXTENSIONS = {
    '.java', '.ts', '.js', '.tsx', '.jsx', '.html', '.css', '.scss',
    '.xml', '.json', '.properties', '.yml', '.yaml', '.cmd', '.bat', '.sh',
    '.mvn', '.jar', '.war', '.class'
}

# Allowed target directory names (Safety Lock 1)
ALLOWED_DIR_NAMES = {'scratch', 'backups'}

def main():
    parser = argparse.ArgumentParser(description="Secure backup files cleanup script.")
    parser.add_argument("--dir", required=True, help="Target directory to clean up.")
    parser.add_argument("--keep", type=int, default=10, help="Number of latest backup files to retain.")
    parser.add_argument("--dry-run", action="store_true", help="Simulate the cleanup without actual deletion.")
    
    args = parser.parse_args()
    
    target_path = Path(args.dir).resolve()
    keep_count = args.keep
    is_dry_run = args.dry_run
    
    print(f"[*] Starting cleanup in directory: {target_path}")
    print(f"[*] Retain limit: {keep_count} files")
    if is_dry_run:
        print("[*] Running in DRY-RUN mode. No files will be deleted.")

    # 🔒 Safety Lock 1: Directory Name Whitelist
    dir_name = target_path.name.lower()
    if dir_name not in ALLOWED_DIR_NAMES:
        print(f"[!] SAFETY ERROR: Directory name '{target_path.name}' is not in the whitelist {ALLOWED_DIR_NAMES}.")
        sys.exit(1)
        
    # 🔒 Safety Lock 2: Root & System Path Protection
    # Length check: Must not target root (e.g. C:\, D:\) or direct project level roots (depth check)
    if len(target_path.parts) <= 2:
        print(f"[!] SAFETY ERROR: Target path '{target_path}' is too shallow or is a root directory.")
        sys.exit(1)
        
    # System folder protection
    forbidden_prefixes = ["c:\\windows", "c:\\program files", "c:\\users", "d:\\$recycle.bin"]
    normalized_path_str = str(target_path).lower()
    for prefix in forbidden_prefixes:
        # Check if they are equal, or if the path is exactly a system directory
        if normalized_path_str == prefix:
            print(f"[!] SAFETY ERROR: Target path '{target_path}' is a protected system directory.")
            sys.exit(1)

    if not target_path.exists():
        print(f"[!] Directory '{target_path}' does not exist. Nothing to clean.")
        sys.exit(0)

    # 🔒 Safety Lock 3: No Recursive Folder Deletion & Only process direct child files
    all_items = list(target_path.iterdir())
    files = []
    
    for item in all_items:
        if item.is_dir():
            # Skip directories entirely. We never delete subdirectories.
            continue
        if item.is_file():
            # 🔒 Safety Lock 4: File Target Filtering (Exclude active code files)
            suffix = item.suffix.lower()
            # Double-suffix check: e.g. App.java.bak suffix is .bak (allowed), but App.java suffix is .java (blocked)
            if suffix in CRITICAL_EXTENSIONS:
                print(f"[-] SAFETY SKIP: Skipping critical file type '{item.name}'")
                continue
            files.append(item)

    if not files:
        print("[*] No targetable backup files found.")
        sys.exit(0)

    # Sort files by modification time (mtime) descending (newest first)
    files.sort(key=lambda x: x.stat().st_mtime, reverse=True)

    keep_files = files[:keep_count]
    delete_files = files[keep_count:]

    print(f"\n[+] Retaining latest {len(keep_files)} files:")
    for f in keep_files:
        print(f"  - [KEEP] {f.name} (Modified: {f.stat().st_mtime})")

    if not delete_files:
        print("\n[*] No files exceed the retention count limit. No deletion needed.")
        sys.exit(0)

    print(f"\n[-] Found {len(delete_files)} files exceeding limit to be cleaned:")
    for f in delete_files:
        action_prefix = "[WOULD DELETE]" if is_dry_run else "[DELETE]"
        print(f"  - {action_prefix} {f.name}")

    # 🔒 Safety Lock 5: Dry-Run Mode Enforcement
    if is_dry_run:
        print("\n[*] Dry-run execution completed. No files were removed.")
        sys.exit(0)

    # Perform actual deletion
    deleted_count = 0
    error_count = 0
    print("\n[*] Performing actual deletion...")
    for f in delete_files:
        try:
            f.unlink()
            print(f"  - [DELETED] {f.name}")
            deleted_count += 1
        except Exception as e:
            print(f"  - [ERROR] Failed to delete {f.name}: {e}")
            error_count += 1

    print(f"\n[*] Cleanup completed. Successfully deleted {deleted_count} files, errors: {error_count}.")

if __name__ == "__main__":
    main()
