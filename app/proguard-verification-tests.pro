# 仅测试 APK：JUnit 及 class 参数通过反射发现入口，不需要混淆测试用例本身。
-keepattributes RuntimeVisibleAnnotations,Signature,InnerClasses,EnclosingMethod
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class me.kafuuneko.rpclient.** { *; }
