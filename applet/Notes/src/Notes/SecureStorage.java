package Notes;

import com.intel.util.DebugPrint;
import com.intel.util.IOException;
import com.intel.langutil.ArrayUtils;
import com.intel.langutil.List;
import com.intel.langutil.TypeConverter;
import java.util.Hashtable;
import java.util.*; 

public class SecureStorage {
	Hashtable<Integer, Boolean> existingFiles;  // the original FS (list of file names).
	Hashtable<Integer, byte[]> loadedFiles;  // the loaded files.
	
	Hashtable<Integer, Boolean> newFiles = new Hashtable<Integer, Boolean>(); // list of modified files that where not in the original FS.
	Hashtable<Integer, Boolean> filesToDelete = new Hashtable<Integer, Boolean>(); // list of deleted files.
	Hashtable<Integer, Boolean> modifiedFiles = new Hashtable<Integer, Boolean>(); // list of modified files.


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
	
	public byte[] insertFSInfoToBuffer(byte[] response) {
		//calculate buffer size
		int filesToDelete_n = filesToDelete.size();
		int modifiedFiles_n = modifiedFiles.size();
		int bufferSize = 8 + filesToDelete_n + 8 * modifiedFiles_n + response.length;
		Enumeration<Integer> modifiedFiles_list = modifiedFiles.keys();
		while (modifiedFiles_list.hasMoreElements())
			bufferSize += loadedFiles.get(modifiedFiles_list.nextElement()).length;
		byte[] result = new byte[bufferSize];
		
		// *** insert the data ***
		// the header
		TypeConverter.intToBytes(filesToDelete_n, result, 0);
		TypeConverter.intToBytes(modifiedFiles_n, result, 4);
		
		// list of file names to delete
		int offset = 8;
		Enumeration<Integer> filesToDelete_list = filesToDelete.keys();
		while (filesToDelete_list.hasMoreElements()) {
			TypeConverter.intToBytes(filesToDelete_list.nextElement(), result, offset);
			offset += 4;
		}
		
		// the modified files (name, length, data)
		modifiedFiles_list = modifiedFiles.keys();
		while (modifiedFiles_list.hasMoreElements()) {
			DebugPrint.printString("offset: " + offset);
			int fileName = modifiedFiles_list.nextElement();
			byte[] file = loadedFiles.get(fileName);
			int fileLen = file.length;
			TypeConverter.intToBytes(fileName, result, offset);
			TypeConverter.intToBytes(fileLen, result, offset + 4);
			DebugPrint.printString("offset + 8 + fileLen = " + (offset + 8 + fileLen));
			ArrayUtils.copyByteArray(file, 0, result, offset + 8, fileLen);
			offset += (8 + fileLen);
		}
		// the user buffer
		ArrayUtils.copyByteArray(response, 0, result, offset, response.length);
		DebugPrint.printString("is printing??? 93 in SS");

	 return	result;
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
		else if (!filesToDelete.containsKey(fileName))
			newFiles.put(fileName, true);
			
		loadedFiles.put(fileName, encrypt(file));
		existingFiles.put(fileName, true);
		modifiedFiles.put(fileName, true);
	}
	
	public void delete(int fileName) {
		if (!existingFiles.containsKey(fileName))
			throw new IOException("The file " + String.valueOf(fileName) + " doesn't exists.");
		
		if (!newFiles.containsKey(fileName))
			filesToDelete.put(fileName, true);
		
		existingFiles.remove(fileName);
		loadedFiles.remove(fileName);
		modifiedFiles.remove(fileName);
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
