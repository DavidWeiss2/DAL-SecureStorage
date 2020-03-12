package Notes;

import com.intel.util.IOException;
import com.intel.langutil.ArrayUtils;
import com.intel.langutil.List;
import com.intel.langutil.TypeConverter;
import java.util.Hashtable;

public class SecureStorage {
	Hashtable<Integer, Boolean> existingFiles;
	Hashtable<Integer, byte[]> loadedFiles;
	
	Hashtable<Integer, Boolean> filesToDelete; // list of deleted files.
	Hashtable<Integer, Boolean> modifiedFiles; // list of modified files.


	public byte[] extractFSInfoFromBuffer(byte[] request) {
		byte[] userRequest;
		if (request[0] == (byte)1) // there is FS to fetch (enableWrite).
		{
			int FS_size = TypeConverter.bytesToInt(request, 1);  // number of existingFiles
			existingFiles = new Hashtable<Integer, Boolean>(FS_size);
			int offset;
			for (offset = 9; offset < 9 + 4 * FS_size; offset += 4)
				existingFiles.put(TypeConverter.bytesToInt(request, offset), true);

			int importedFilesNum = TypeConverter.bytesToInt(request, 5);
			loadedFiles = new Hashtable<Integer, byte[]>(importedFilesNum);
			for (int i = 0; i < importedFilesNum; i++) {
				int fileName = TypeConverter.bytesToInt(request, offset);
				int fileLen = TypeConverter.bytesToInt(request, offset + 4);
				byte[] file = new byte[fileLen];
				ArrayUtils.copyByteArray(request, offset + 8, file, 0, fileLen);
				loadedFiles.put(fileName, file);
				offset += (8 + fileLen);
			}
			
			int userRequestLen = request.length - offset;
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
	
	public byte[] read(int fileName) {
		if (!existingFiles.containsKey(fileName))
			throw new IOException("The file " + String.valueOf(fileName) + " doesn't exists.");
		else if (!loadedFiles.containsKey(fileName))
			throw new IOException("The file " + String.valueOf(fileName) + " doesn't loaded.");
		return decrypt(loadedFiles.get(fileName));
	}
	
	public void write(int fileName, byte[] file) {
		if (existingFiles.containsKey(fileName))
			throw new IOException("The file " + String.valueOf(fileName) + " already exists.");
		loadedFiles.put(fileName, encrypt(file));
		existingFiles.put(fileName, true);
		modifiedFiles.put(fileName, true);
	}
	
	public void delete(int fileName) {
		if (!existingFiles.containsKey(fileName))
			throw new IOException("The file " + String.valueOf(fileName) + " doesn't exists.");
		existingFiles.remove(fileName);
		loadedFiles.remove(fileName);
		modifiedFiles.remove(fileName);
		filesToDelete.put(fileName, true);
	}
	
	
	private byte[] encrypt(byte[] plainText) {
		// todo implement
		return plainText;
	}
	
	private byte[] decrypt(byte[] cipherText) {
		// todo implement
		return cipherText;
	}
	


}
