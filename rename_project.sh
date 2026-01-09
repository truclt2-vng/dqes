#!/bin/bash

# Get the absolute path of the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Get the current folder name
CURRENT_FOLDER="$(basename "$SCRIPT_DIR")"
# Print the current folder name
echo "Current folder name: $CURRENT_FOLDER"

OLD_NAME="xpprjtempl"
NEW_NAME=$CURRENT_FOLDER

CAP_OLD_NAME="${OLD_NAME^}" # Capitalize the first character
CAP_NEW_NAME="${NEW_NAME^}" # Capitalize the first character

echo "$CAP_OLD_NAME"  
echo "$CAP_NEW_NAME" 

# Confirm user input
echo "Renaming project from '$OLD_NAME' to '$NEW_NAME'..."
read -p "Are you sure? (y/n): " CONFIRM
if [[ "$CONFIRM" != "y" ]]; then
  echo "Aborted."
  exit 1
fi

#EXCLUDED_FILES="excluded1.txt|excluded2.conf|excluded3.sh"  # Modify this list as needed
EXCLUDED_FILES="rename_project.sh"  # Modify this list as needed
echo "Updating file contents recursively..."
grep -rEl "$OLD_NAME|$CAP_OLD_NAME" . | grep -Ev "$EXCLUDED_FILES" | while read -r file; do
  sed -i "s/$OLD_NAME/$NEW_NAME/g" "$file"
  sed -i "s/$CAP_OLD_NAME/$CAP_NEW_NAME/g" "$file"
done


# Step 2: Rename directories and files
echo "Renaming directories and files recursively..."
find . -depth -name "*$OLD_NAME*" | sort -r | while read -r path; do
  NEW_PATH=$(echo "$path" | sed "s/$OLD_NAME/$NEW_NAME/g")
  echo "Renaming: $path -> $NEW_PATH"
  mv "$path" "$NEW_PATH"
done
find . -depth -name "*$CAP_OLD_NAME*" | sort -r | while read -r path; do
  NEW_PATH=$(echo "$path" | sed "s/$CAP_OLD_NAME/$CAP_NEW_NAME/g")
  echo "Renaming: $path -> $NEW_PATH"
  mv "$path" "$NEW_PATH"
done

# Step 3: Rename the root project directory (if applicable)
#if [[ "$(basename "$PWD")" == "$OLD_NAME" ]]; then
#  echo "Renaming root project directory..."
#  cd ..
#  mv "$OLD_NAME" "$NEW_NAME"
#  cd "$NEW_NAME" || exit
#fi

echo "Project renamed successfully!"
