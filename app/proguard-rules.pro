# 发现音乐混淆规则（release 构建）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.luogen.music.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.luogen.music.**$$serializer { *; }
-keepclassmembers class com.luogen.music.** { *** Companion; }
-keep class com.google.zxing.** { *; }
-keep class androidx.media3.** { *; }

# Room 数据库：实体字段名/DAO 生成代码按类型直接引用，防止混淆破坏列映射
-keep class com.luogen.music.data.db.** { *; }
# 领域模型：参与 JSON 序列化与歌单快照持久化
-keep class com.luogen.music.domain.model.** { *; }
# 桌面小组件与广播接收器（Launcher 跨进程引用）
-keep class com.luogen.music.widget.** { *; }