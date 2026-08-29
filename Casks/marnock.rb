cask "marnock" do
  version "1.3.2"
  # Pinned by CI on each v* GitHub Release (`scripts/update-homebrew-cask.sh`).
  sha256 "36b2dd2f53138f04f40f6f161042bbd95605d8ea4391b1c12c51229275b7e26e"

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
