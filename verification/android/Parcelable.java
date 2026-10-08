package android.os;
public interface Parcelable {
    int describeContents();
    void writeToParcel(Parcel parcel, int flags);
    interface Creator<T> {
        T createFromParcel(Parcel parcel);
        T[] newArray(int size);
    }
    interface ClassLoaderCreator<T> extends Creator<T> {
        T createFromParcel(Parcel parcel, ClassLoader loader);
    }
}
