cask "marnock" do
  version "1.2.0"
  # Pinned by CI on each v* GitHub Release (`scripts/update-homebrew-cask.sh`).
  sha256 "97f6882a6430e86e9bf6615756134cc8929de6cf939557b8b7bcc44c84a3f0cd"

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
