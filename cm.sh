while IFS= read -r file; do
    sed -i '/\/\*$,/^\s*\*\/$/{
        /Copyright (C) 2025 Nicholas J Emblow/,/http:\/\/www\.gnu\.org\/licenses\/d
        }' "$file"
done < all_java_files.txt
