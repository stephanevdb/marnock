cask "marnock" do
  version "1.3.5"
  # Pinned by CI on each v* GitHub Release (`scripts/update-homebrew-cask.sh`).
  sha256 "3bedbece312a4ab222e32ca89e6c945f565ad00932ffc05911ae949e524ff740"

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
