import Flutter
import UIKit
import MLKitBarcodeScanning
import MLKitCommon
import MLKitVision

@available(iOS 10.0, *)
public class SwiftCamerakitPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "camerakit", binaryMessenger: registrar.messenger())
    let instance = SwiftCamerakitPlugin()
    registrar.register(CameraKitFactory(registrar: registrar), withId: "plugins/camera_kit")
    registrar.addMethodCallDelegate(instance, channel: channel)
    
    let channelDirect = FlutterMethodChannel(name: "plugins/camera_kit_direct", binaryMessenger: registrar.messenger())
    channelDirect.setMethodCallHandler({ (FlutterMethodCall,  FlutterResult) in
        if FlutterMethodCall.method == "processImage"{
            let args = FlutterMethodCall.arguments
            let myArgs = args as? [String: Any]
            var path = (myArgs?["path"] ) as! String
            let fileURL = URL(fileURLWithPath: path)
            do {
                let imageData = try Data(contentsOf: fileURL)
                let image = UIImage(data: imageData)
                if image == nil {
                    return
                }
                
                let visionImage = VisionImage(image: image!)
                
                let barcodeScanner = BarcodeScanner.barcodeScanner()
                
                //ToDo
//                let orientation = imageOrientation(
//                  fromDevicePosition: cameraPosition
//                )
//                visionImage.orientation = orientation
                var barcodes: [Barcode]
                do {
                    barcodes = try barcodeScanner.results(in: visionImage)
                } catch let error {
                  print("Failed to scan barcodes with error: \(error.localizedDescription).")
                    FlutterError( code: "102", message: "Failed to scan barcodes with error: \(error.localizedDescription).", details: nil )
                  return
                }
                
                guard !barcodes.isEmpty else {
                   //print("Barcode scanner returrned no results.")
                   return
                 }
                
                for barcode in barcodes {
                    FlutterResult(barcode.rawValue!)
                }
        }
            catch {
                print("Error loading image : \(error)")
                FlutterError( code: "101", message: "loading image : \(error)", details: nil )
            }
        }
        
    })
    
  }

   
    
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
