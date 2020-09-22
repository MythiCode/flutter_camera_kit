import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:camerakit/camerakit.dart';

void main() {
  const MethodChannel channel = MethodChannel('camerakit');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await Camerakit.platformVersion, '42');
  });
}
