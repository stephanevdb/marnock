cask "marnock" do
  version "1.1.1"
  # Pinned by CI on each v* GitHub Release (`scripts/update-homebrew-cask.sh`).
  sha256 "e7d91a6666cb12b8093daa32a667912b819069da9a2359616f5d763e2fedc7c4"

  url "https://github.com/stephanevdb/marnock/releases/download/v#{version}/Marnock-macos.zip"
  name "Marnock"
  desc "Sync clipboard, notifications, SMS, and calls with your Android phone"
  homepage "https://github.com/stephanevdb/marnock"

  livecheck do
    url :homepage
    strategy :github_latest
  end

  auto_updates true
  depends_on macos: :ventura

  app "Marnock.app"

  zap trash: "~/Library/Preferences/com.marnock.macos.plist"
end
