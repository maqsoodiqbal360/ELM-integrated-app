p = "android/app/src/main/AndroidManifest.xml"
txt = open(p).read()
perms = '''
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
'''
txt = txt.replace("<application", perms + "\n    <application", 1)
open(p, "w").write(txt)
