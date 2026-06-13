import Foundation

extension Bundle {
    /// The bundle that holds app resources, resolved for whichever build system is in use:
    /// `Bundle.module` under SwiftPM (xtool/`swift build`), `Bundle.main` in the Xcode app
    /// target (XcodeGen + xcodebuild, where resources live in the app bundle).
    static var appResources: Bundle {
        #if SWIFT_PACKAGE
        return .module
        #else
        return .main
        #endif
    }
}
