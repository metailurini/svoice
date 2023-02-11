#!/usr/bin/env bash

function get_plugin_name() {
  sed 's/^[^"]*"//g; s/"//g' settings.gradle.kts
}

function get_idea_plugin_folders() {
  # shellcheck disable=SC2207
  local paths=($(find ~/.local/share -type d | grep -io '.*.*/[0-9]*\.[0-9]*.[0-9]*\.plugins' | sort -u))
  echo "${paths[@]}"
}

function get_latest_jar_build() {
  find ./build/libs -type f -printf "%T@ %Tc %p\n" | sort -n | tail -n1 | awk '{print $(NF)}'
}

function main() {
  path_built=$(get_latest_jar_build)
  # shellcheck disable=SC2207
  folders=($(get_idea_plugin_folders "*.txt"))
  for folder in "${folders[@]}"; do
    find "$folder" \
      | grep -i "$(get_plugin_name).*jar" \
      | xargs -i rm {}

    cp "$path_built" "$folder"
  done
}

main
