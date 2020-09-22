//
//  CameraKitFactory.swift
//  camerakit
//
//  Created by MythiCode on 9/6/20.
//

import Foundation
import Flutter

@available(iOS 10.0, *)
class CameraKitFactory : NSObject, FlutterPlatformViewFactory {
    
    let registrar:FlutterPluginRegistrar
    
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        let cameraKitFlutterView = CameraKitFlutterView(registrar: self.registrar, viewId: viewId, frame: frame)
        cameraKitFlutterView.setMethodHandler()
        return cameraKitFlutterView
    }
    

    
   
    init(registrar: FlutterPluginRegistrar) {
        self.registrar = registrar
    }
}
