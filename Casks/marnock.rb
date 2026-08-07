cask "marnock" do
  version "1.1.0"
  # Pinned by CI on each v* GitHub Release (`scripts/update-homebrew-cask.sh`).
  sha256 "d8b9f147baf5c572e4fa3201eb466b373059d9be8570acd216c7dc1820098179"

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
