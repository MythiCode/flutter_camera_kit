#import "CamerakitPlugin.h"
#if __has_include(<camerakit/camerakit-Swift.h>)
#import <camerakit/camerakit-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "camerakit-Swift.h"
#endif

@implementation CamerakitPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftCamerakitPlugin registerWithRegistrar:registrar];
    
}
@end
