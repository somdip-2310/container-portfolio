#!/bin/bash
# save this as fix-lombok.sh

# Find all files with @RequiredArgsConstructor
echo "Files that need constructor generation:"
grep -r "@RequiredArgsConstructor" src/ --include="*.java" | cut -d: -f1 | sort | uniq

# Comment out @RequiredArgsConstructor in all files
find src -name "*.java" -type f -exec sed -i '' 's/@RequiredArgsConstructor/\/\/ @RequiredArgsConstructor - TODO: Generate constructor/g' {} \;

# Remove or comment the import
find src -name "*.java" -type f -exec sed -i '' 's/import lombok.RequiredArgsConstructor;/\/\/ import lombok.RequiredArgsConstructor;/g' {} \;

echo "Done! Now generate constructors in your IDE for each file listed above."
