class Marnock < Formula
  desc "Sync clipboard, notifications, SMS, and calls between Android and Mac"
  homepage "https://github.com/stephanevdb/marnock"
  url "https://github.com/stephanevdb/marnock/archive/refs/tags/v1.1.0.tar.gz"
  sha256 "d5bebb39fe272388a401c6db092646e2056c88855bdfb5dd4cf77c89d4c7aabb"
  head "https://github.com/stephanevdb/marnock.git", branch: "main"

  depends_on xcode: ["15.0", :build]
  depends_on macos: :ventura

  def install
    cd "macos/Marnock" do
      system "swift", "build", "-c", "release", "--disable-sandbox"
      bin_dir = Utils.safe_popen_read(
        "swift", "build", "-c", "release", "--show-bin-path", "--disable-sandbox"
      ).strip

      app = buildpath/"Marnock.app"
      rm_r app if app.exist?
      (app/"Contents/MacOS").mkpath
      (app/"Contents/Resources").mkpath

      cp "Marnock/Info.plist", app/"Contents/Info.plist"
      # Stamp the formula version into the bundle.
      system "/usr/libexec/PlistBuddy", "-c",
             "Set :CFBundleShortVersionString #{version}", app/"Contents/Info.plist"
      system "/usr/libexec/PlistBuddy", "-c",
             "Set :CFBundleVersion #{version}", app/"Contents/Info.plist"

      cp "#{bin_dir}/Marnock", app/"Contents/MacOS/Marnock"
      chmod 0755, app/"Contents/MacOS/Marnock"

      icns = Pathname.new("Marnock/Resources/AppIcon.icns")
      icns = buildpath/"branding/Marnock.icns" unless icns.exist?
      cp icns, app/"Contents/Resources/AppIcon.icns" if icns.exist?

      system "codesign", "--force", "--deep", "--sign", "-",
             "--entitlements", "Marnock/Marnock.entitlements", app

      prefix.install app
    end

    (bin/"marnock").write <<~EOS
      #!/bin/bash
      exec open "#{prefix}/Marnock.app" "$@"
    EOS
  end

  def caveats
    <<~EOS
      Marnock.app was installed to:
        #{prefix}/Marnock.app

      Launch with `marnock`, or link into /Applications:
        ln -sf "#{prefix}/Marnock.app" /Applications/Marnock.app

      Open it once to grant Local Network and Notification permissions.
    EOS
  end

  test do
    assert_path_exists prefix/"Marnock.app/Contents/MacOS/Marnock"
  end
end
