# Marnock Share Extension

The main app registers the `marnock://` URL scheme and accepts opened files.

## Share to phone from Finder / other apps

1. Prefer drag-and-drop into the **Files** sidebar in Marnock.
2. Or invoke:
   ```bash
   open "marnock://send?path=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "/path/to/file")"
   ```
3. Open a URL on the phone:
   ```bash
   open "marnock://open?url=https%3A%2F%2Fexample.com"
   ```

A full `.appex` Share Extension can be added later in an Xcode app target using App Group `group.com.marnock.macos` writing into `~/Library/Group Containers/.../Inbox`.
