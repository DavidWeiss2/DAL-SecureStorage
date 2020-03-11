package Notes;

import com.intel.util.IOException;
import com.intel.langutil.ArrayUtils;
import com.intel.langutil.List;
import com.intel.langutil.TypeConverter;
import java.util.Hashtable;

public class secureStorage {
	int[] FS;
	Hashtable<Integer, byte[]> loadedFiles;

	public byte[] extractFSInfoFromBuffer(byte[] request) {
		byte[] userRequest;
		if (request[0] == (byte)1) // there is FS to fetch (enableWrite).
		{
			int FS_size = TypeConverter.bytesToInt(request, 1);
			int importedFilesNum = TypeConverter.bytesToInt(request, 5);
			FS = new int[FS_size];
			for(int i = 0; i < FS_size; i++) {
				FS[i] = TypeConverter.bytesToInt(request, 9 + 4*i);
			}
			int offset = 9 + 4 * FS_size;
			loadedFiles = new Hashtable<Integer, byte[]>(importedFilesNum);
			for (int i = 0; i < importedFilesNum; i++) {
				int fileName = TypeConverter.bytesToInt(request, offset);
				int fileLen = TypeConverter.bytesToInt(request, offset + 4);
				byte[] file = new byte[fileLen];
				ArrayUtils.copyByteArray(request, offset + 8, file, 0, fileLen);
				loadedFiles.put(fileName, file);
				offset += (8 + fileLen);
			}
			int userRequestLen = request.length - (offset + 1);
			userRequest = new byte[userRequestLen];
			ArrayUtils.copyByteArray(request, offset, userRequest, 0, userRequestLen);
		}
		else if (request[0] == (byte)0)
		{
			userRequest = new byte[request.length - 1];
			ArrayUtils.copyByteArray(request, 1, userRequest, 0, request.length - 1);
		}
		else // unknown code
			throw new IOException("When using SecureStorage, you must also use it on the Host side.");
		return userRequest;
	}
	


}
