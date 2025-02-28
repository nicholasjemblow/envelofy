(
  # Process .java files: print header, then contents
  find . -name "*.java" -type f -exec sh -c 'echo "===== $1 ====="; cat "$1"' sh {} \;

  # Process properties and SQL files similarly
  for file in src/main/resources/application.properties src/main/resources/schema.sql; do
    echo "===== $file ====="
    cat "$file"
  done

  # Process frontend files (.js and .html)
  find frontend -maxdepth 1 -type f \( -name "*.js" -o -name "*.html" \) -exec sh -c 'echo "===== $1 ====="; cat "$1"' sh {} \;
) > all_java_files.txt
