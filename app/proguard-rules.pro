-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

-keepclassmembers class * implements java.io.Serializable {
    private static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keep class org.json.** { *; }

-keep class io.github.martinezanthony.sqliteviewer.ui.main.MainActivity
-keep class io.github.martinezanthony.sqliteviewer.ui.viewer.DatabaseViewerActivity

-keep class io.github.martinezanthony.sqliteviewer.ui.main.RecentFilesAdapter$ViewHolder
-keep class io.github.martinezanthony.sqliteviewer.ui.viewer.TableResultsAdapter$RowViewHolder

-keep class io.github.martinezanthony.sqliteviewer.ui.main.RecentFilesAdapter { *; }
-keep class io.github.martinezanthony.sqliteviewer.ui.viewer.TableResultsAdapter { *; }

-keep class io.github.martinezanthony.sqliteviewer.ui.viewer.ExportFormat { *; }

-keep class io.github.martinezanthony.sqliteviewer.data.RecentFilesRepository { *; }
-keep class io.github.martinezanthony.sqliteviewer.utils.DatabaseUtils { *; }
-keep class io.github.martinezanthony.sqliteviewer.utils.FileUtils { *; }

-dontwarn java.lang.invoke.**
-dontwarn sun.misc.Unsafe