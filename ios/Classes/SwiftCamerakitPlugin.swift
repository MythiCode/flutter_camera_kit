import Flutter
import UIKit

@available(iOS 10.0, *)
public class SwiftCamerakitPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "camerakit", binaryMessenger: registrar.messenger())
    let instance = SwiftCamerakitPlugin()
    registrar.register(CameraKitFactory(registrar: registrar), withId: "plugins/camera_kit")
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
