//
//  CameraKitFlutterView.swift
//  camerakit
//
//  Created by MythiCode on 9/6/20.
//

import Foundation
import AVFoundation
import MLKitBarcodeScanning
import MLKitCommon
import MLKitVision


@available(iOS 10.0, *)
class CameraKitFlutterView : NSObject, FlutterPlatformView, AVCaptureVideoDataOutputSampleBufferDelegate, AVCapturePhotoCaptureDelegate{
    let channel: FlutterMethodChannel
    let frame: CGRect

    var hasBarcodeReader:Bool!
    var imageSavePath:String!
    var isCameraVisible:Bool! = true
    var initCameraFinished:Bool! = false
    var isFillScale:Bool!
    var flashMode:AVCaptureDevice.FlashMode!
    var cameraPosition: AVCaptureDevice.Position!
    
    var previewView : UIView!
    var videoDataOutput: AVCaptureVideoDataOutput!
    var videoDataOutputQueue: DispatchQueue!
    
    var photoOutput: AVCapturePhotoOutput?
    var previewLayer:AVCaptureVideoPreviewLayer!
    var captureDevice : AVCaptureDevice!
    let session = AVCaptureSession()
    var barcodeScanner:BarcodeScanner!
    var flutterResultTakePicture:FlutterResult!
    
    init(registrar: FlutterPluginRegistrar, viewId: Int64, frame: CGRect) {
         self.channel = FlutterMethodChannel(name: "plugins/camera_kit_" + String(viewId), binaryMessenger: registrar.messenger())
        self.frame = frame
         

    
     }
    
    func requestPermission(flutterResult:  @escaping FlutterResult) {
        if AVCaptureDevice.authorizationStatus(for: .video) ==  .authorized {
            //already authorized
            flutterResult(true)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                AVCaptureDevice.requestAccess(for: .video, completionHandler: { (granted: Bool) in
                    if granted {
                        //access allowed
                        flutterResult(true)
                    } else {
                        //access denied
                        flutterResult(false)
                    }
                })
            }
        }
    }
    
    
    public func setMethodHandler() {
        self.channel.setMethodCallHandler({ (FlutterMethodCall,  FlutterResult) in
                let args = FlutterMethodCall.arguments
                let myArgs = args as? [String: Any]
                if FlutterMethodCall.method == "requestPermission" {
                    self.requestPermission(flutterResult: FlutterResult)
                } else if FlutterMethodCall.method == "initCamera" {
                    self.initCameraFinished = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                        self.initCamera(hasBarcodeReader: (myArgs?["hasBarcodeReader"] as! Bool),
                                        flashMode: (myArgs?["flashMode"] ) as! String,isFillScale:
                                        (myArgs?["isFillScale"] ) as! Bool
                            , barcodeMode:   (myArgs?["barcodeMode"] ) as! Int
                            , cameraSelector:   (myArgs?["cameraSelector"] ) as! Int
                            )
                    }
                } else if FlutterMethodCall.method == "resumeCamera" {
                    if  self.initCameraFinished == true {
                        //self.beginSession(isFirst: false)
                        self.session.startRunning()
                        self.isCameraVisible = true
                    }
            }
                else if FlutterMethodCall.method == "pauseCamera" {
                     if self.initCameraFinished == true {
                        self.stopCamera()
                        self.isCameraVisible = false
                    }
                }
            else if FlutterMethodCall.method == "changeFlashMode" {
                    self.setFlashMode(flashMode: (myArgs?["flashMode"] ) as! String)
                    self.changeFlashMode()
                } else if FlutterMethodCall.method == "setCameraVisible" {
                    let cameraVisibility = (myArgs?["isCameraVisible"] as! Bool)
                    //print("isCameraVisible: " + String(isCameraVisible))
                    if cameraVisibility == true {
                        if self.isCameraVisible == false {
                            self.session.startRunning()
                            self.isCameraVisible = true
                        }
                    } else {
                           if self.isCameraVisible == true {
                                self.stopCamera()
                                self.isCameraVisible = false
                        }
                    }
                  
                }
             else if FlutterMethodCall.method == "takePicture" {
                self.imageSavePath = (myArgs?["path"] ) as! String
                self.flutterResultTakePicture = FlutterResult
                self.takePicture()
                        }
            })
    }
    
    func changeFlashMode() {
       
        if(self.hasBarcodeReader) {
            do{
               if (captureDevice.hasFlash)
                   {
                       try captureDevice.lockForConfiguration()
                    captureDevice.torchMode = (self.flashMode == .auto) ?(.auto):(self.flashMode == .on ? (.on) : (.off))
                       captureDevice.flashMode = self.flashMode
                       captureDevice.unlockForConfiguration()
                   }
                }catch{
                   //DISABEL FLASH BUTTON HERE IF ERROR
                   print("Device tourch Flash Error ");
               }
          
        }
    }
    
    func setFlashMode(flashMode: String) {
        if flashMode == "A" {
            self.flashMode = .auto
                  } else if flashMode == "O" {
            self.flashMode = .on
                  } else if flashMode == "F"{
            self.flashMode = .off
                  }
    }
    
    func view() -> UIView {
        if previewView == nil {
        self.previewView = UIView(frame: frame)
//            previewView.contentMode = UIView.ContentMode.scaleAspectFill
        }
        return previewView
    }
    
    func initCamera(hasBarcodeReader: Bool, flashMode: String, isFillScale: Bool, barcodeMode: Int, cameraSelector: Int) {
        self.hasBarcodeReader = hasBarcodeReader
        self.isFillScale = isFillScale
        self.cameraPosition = cameraSelector == 0 ? .back : .front
        var myBarcodeMode: Int
        setFlashMode(flashMode: flashMode)
        if hasBarcodeReader == true{
//            let barcodeOptions = BarcodeScannerOptions(formats:
//                BarcodeFormat(rawValue: barcodeMode))
              if barcodeMode == 0 {
                 myBarcodeMode = 65535
             }
              else {
                myBarcodeMode = barcodeMode
            }
             let barcodeOptions = BarcodeScannerOptions(formats:
                BarcodeFormat(rawValue: myBarcodeMode))
//            let barcodeOptions = BarcodeScannerOptions(formats:
//                .all)
                // Create a barcode scanner.
               barcodeScanner = BarcodeScanner.barcodeScanner(options: barcodeOptions)
        }
            self.setupAVCapture()
    }
    
    @available(iOS 10.0, *)
    func setupAVCapture(){
        session.sessionPreset = AVCaptureSession.Preset.hd1920x1080
          guard let device = AVCaptureDevice
          .default(AVCaptureDevice.DeviceType.builtInWideAngleCamera,
                   for: .video,
                   position: cameraPosition) else {
                              return
          }
          captureDevice = device
    
       
          beginSession()
          changeFlashMode()
      }
    
    
    func beginSession(isFirst: Bool = true){
        var deviceInput: AVCaptureDeviceInput!

        
        do {
            deviceInput = try AVCaptureDeviceInput(device: captureDevice)
            guard deviceInput != nil else {
                print("error: cant get deviceInput")
                return
            }
            
            if self.session.canAddInput(deviceInput){
                self.session.addInput(deviceInput)
            }

            if(hasBarcodeReader) {
                videoDataOutput = AVCaptureVideoDataOutput()
                videoDataOutput.alwaysDiscardsLateVideoFrames=true
               
                videoDataOutputQueue = DispatchQueue(label: "VideoDataOutputQueue")
                videoDataOutput.setSampleBufferDelegate(self, queue:self.videoDataOutputQueue)
                if session.canAddOutput(videoDataOutput!){
                             session.addOutput(videoDataOutput!)
                 }
                videoDataOutput.connection(with: .video)?.isEnabled = true

            }
            else {
                photoOutput = AVCapturePhotoOutput()
                    photoOutput?.setPreparedPhotoSettingsArray([AVCapturePhotoSettings(format: [AVVideoCodecKey : AVVideoCodecJPEG])], completionHandler: nil)
                if session.canAddOutput(photoOutput!){
                    session.addOutput(photoOutput!)
                }
            }



            previewLayer = AVCaptureVideoPreviewLayer(session: self.session)
            if self.isFillScale == true {
            previewLayer.videoGravity = AVLayerVideoGravity.resizeAspectFill
            } else {
                   previewLayer.videoGravity = AVLayerVideoGravity.resizeAspect
            }

            startSession(isFirst: isFirst)
       
            
        } catch let error as NSError {
            deviceInput = nil
            print("error: \(error.localizedDescription)")
        }
    }
    
    func startSession(isFirst: Bool) {
        DispatchQueue.main.async {
        let rootLayer :CALayer = self.previewView.layer
        rootLayer.masksToBounds = true
        if(rootLayer.bounds.size.width != 0 && rootLayer.bounds.size.width != 0){
            self.previewLayer.frame = rootLayer.bounds
            rootLayer.addSublayer(self.previewLayer)
            self.session.startRunning()
            if isFirst == true {
            DispatchQueue.global().asyncAfter(deadline: .now() + 0.2) {
                    self.initCameraFinished = true
                           }
            }
        } else {
            DispatchQueue.global().asyncAfter(deadline: .now() + 1.0) {
                self.startSession(isFirst: isFirst)
                           }
            }
        }
    }
    
    func stopCamera(){
        if session.isRunning {
            session.stopRunning()
        }
    }

    
     private func currentUIOrientation() -> UIDeviceOrientation {
        let deviceOrientation = { () -> UIDeviceOrientation in
          switch UIApplication.shared.statusBarOrientation {
          case .landscapeLeft:
            return .landscapeRight
          case .landscapeRight:
            return .landscapeLeft
          case .portraitUpsideDown:
            return .portraitUpsideDown
          case .portrait, .unknown:
            return .portrait
          @unknown default:
            fatalError()
          }
        }
        guard Thread.isMainThread else {
          var currentOrientation: UIDeviceOrientation = .portrait
          DispatchQueue.main.sync {
            currentOrientation = deviceOrientation()
          }
          return currentOrientation
        }
        return deviceOrientation()
      }
    
    
    public func imageOrientation(
      fromDevicePosition devicePosition: AVCaptureDevice.Position = .back
    ) -> UIImage.Orientation {
      var deviceOrientation = UIDevice.current.orientation
      if deviceOrientation == .faceDown || deviceOrientation == .faceUp
        || deviceOrientation
          == .unknown
      {
        deviceOrientation = currentUIOrientation()
      }
      switch deviceOrientation {
      case .portrait:
        return devicePosition == .front ? .leftMirrored : .right
      case .landscapeLeft:
        return devicePosition == .front ? .downMirrored : .up
      case .portraitUpsideDown:
        return devicePosition == .front ? .rightMirrored : .left
      case .landscapeRight:
        return devicePosition == .front ? .upMirrored : .down
      case .faceDown, .faceUp, .unknown:
        return .up
      @unknown default:
        fatalError()
      }
    }
    func saveImage(image: UIImage) -> Bool {
        guard let data = image.jpegData(compressionQuality: 1) ?? image.pngData() else {
            return false
        }
        var fileURL : URL? = nil
        if self.imageSavePath == "" {
            guard let directory = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false) as NSURL else {
                return false
            }
            fileURL = directory.appendingPathComponent("pic.jpg")!
        } else  {
            fileURL = URL(fileURLWithPath: self.imageSavePath)
        }
     
     
        
        
        
        
        
        guard let directory = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false) as NSURL else {
            return false
        }
        do {
            try data.write(to: fileURL!)
            flutterResultTakePicture(fileURL?.path)
            //print(directory)
            return true
        } catch {
            print(error.localizedDescription)
                        flutterResultTakePicture(FlutterError(code: "-103", message: error.localizedDescription, details: nil))
            return false
        }
    }
    func takePicture() {
        let settings = AVCapturePhotoSettings()
        if captureDevice.hasFlash {
            settings.flashMode = self.flashMode
        }
        photoOutput?.capturePhoto(with: settings, delegate:self)
    }
    
    public func photoOutput(_ captureOutput: AVCapturePhotoOutput, didFinishProcessingPhoto photoSampleBuffer: CMSampleBuffer?, previewPhoto previewPhotoSampleBuffer: CMSampleBuffer?,
                        resolvedSettings: AVCaptureResolvedPhotoSettings, bracketSettings: AVCaptureBracketedStillImageSettings?, error: Swift.Error?) {
        if let error = error { //self.photoCaptureCompletionBlock?(nil, error)
            flutterResultTakePicture(FlutterError(code: "-101", message: error.localizedDescription, details: nil))
        }
            
        else if let buffer = photoSampleBuffer, let data = AVCapturePhotoOutput.jpegPhotoDataRepresentation(forJPEGSampleBuffer: buffer, previewPhotoSampleBuffer: nil),
            let image = UIImage(data: data) {
            
            self.saveImage(image: image)
        }
            
        else {
            //error
//            self.photoCaptureCompletionBlock?(nil, CameraControllerError.unknown)
                        flutterResultTakePicture(FlutterError(code: "-102", message: "Unknown error", details: nil))
        }
    }
    

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        

        
        // do stuff here
        if barcodeScanner != nil {
            let visionImage = VisionImage(buffer: sampleBuffer)
            let orientation = imageOrientation(
              fromDevicePosition: cameraPosition
            )
            visionImage.orientation = orientation
            var barcodes: [Barcode]
            do {
                barcodes = try self.barcodeScanner.results(in: visionImage)
            } catch let error {
              print("Failed to scan barcodes with error: \(error.localizedDescription).")
              return
            }
            
            guard !barcodes.isEmpty else {
               //print("Barcode scanner returrned no results.")
               return
             }
            
            for barcode in barcodes {
                barcodeRead(barcode: barcode.rawValue!)
            }
        }
        
    }
    
    func barcodeRead(barcode: String) {
        channel.invokeMethod("onBarcodeRead", arguments: barcode)
    }
    
}
