package Notes;

import com.intel.util.DebugPrint;
import com.intel.util.IOException;
import com.intel.util.IntelApplet;
import com.intel.util.MTC;
import com.intel.crypto.SymmetricBlockCipherAlg;
import com.intel.langutil.ArrayUtils;
import com.intel.langutil.List;
import com.intel.langutil.TypeConverter;
import java.util.Hashtable;
import java.util.*; 
import com.intel.util.*;

public class SecureStorage extends IntelApplet {


	private static final int CMD_LOAD_DATA = 1;
	private static final int CMD_SAVE_DATA = 2;
	private static final int CMD_RESET_MTC = 3;
	
	Hashtable<Integer, Boolean> existingFiles;  // the original FS (list of file names).
	Hashtable<Integer, byte[]> loadedFiles;  // the loaded files.
	
	Hashtable<Integer, Boolean> newFiles = new Hashtable<Integer, Boolean>(); // list of modified files that where not in the original FS.
	Hashtable<Integer, Boolean> filesToDelete = new Hashtable<Integer, Boolean>(); // list of deleted files.
	Hashtable<Integer, Boolean> modifiedFiles = new Hashtable<Integer, Boolean>(); // list of modified files.

	
	/**
	 * This method will be called by the VM to handle a command sent to this
	 * Trusted Application instance.
	 * 
	 * @param	commandId	the command ID (Trusted Application specific) 
	 * @param	request		the input data for this command 
	 * @return	the return value should not be used by the applet
	 */
	public int invokeCommand(int commandId, byte[] request) {
		
		DebugPrint.printString("Protected Storage TA: invokeCommand");
		if (request != null)
		{
			DebugPrint.printString("Received buffer:");
			DebugPrint.printBuffer(request);
		}
		DebugPrint.printString("Received command ID: " + commandId);

		int res;
		switch (commandId)
		{
			case CMD_LOAD_DATA:
				res = loadData(request);
				break;
	
			case CMD_SAVE_DATA:
				res = saveData(request);
				break;
	
			case CMD_RESET_MTC:
				res = resetMTC();
				break;
	
			default:
				DebugPrint.printString("ERROR: Invalid command received.");
				res = IntelApplet.APPLET_ERROR_BAD_PARAMETERS;
				break;
		}
		
		setResponseCode(res);
		 
		 /*
		 * The return value of the invokeCommand method is not guaranteed to be
		 * delivered to the SW application, and therefore should not be used for
		 * this purpose. Trusted Application is expected to return APPLET_SUCCESS code 
		 * from this method and use the setResposeCode method instead.
		 */
		return APPLET_SUCCESS;
	}

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
		DebugPrint.printString("is printing??? line 107 in SS");
		DebugPrint.printString("file.length = " + file.length);
		DebugPrint.printString("file = " + file);
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
		int status = IntelApplet.APPLET_ERROR_GENERIC;
		try
		{
			if (plainText == null)
			{
				throw new Exception("An empty data buffer was sent.");
			}
			DebugPrint.printString("Received data buffer:");
			DebugPrint.printBuffer(plainText);

			// Increase the monotonic counter value to make all previous data invalid
			MTC.incrementMTC();

			// Create Platform Binded cipher
			SymmetricBlockCipherAlg SymmetricCipher = SymmetricBlockCipherAlg.create(SymmetricBlockCipherAlg.ALG_TYPE_PBIND_AES_256_CBC);

			// Data is: MTC value | buffer size | data
			int dataSize = plainText.length + TypeConverter.INT_BYTE_SIZE * 2;
			// Align the data size to block buffer size
			short blockSize = SymmetricCipher.getBlockSize();
			if (dataSize % blockSize != 0)
				dataSize = dataSize + blockSize - (dataSize % blockSize);
			
			// An array to hold the data to encrypt
			byte[] data = new byte[dataSize];
			// An array for the encrypted data, 
			// Data size stays the same after encryption because we are using a symmetric key
			byte[] response = new byte[dataSize];

			// First four bytes are the monotonic counter value
			TypeConverter.intToBytes(MTC.getMTC(), data, 0);
			// Second four bytes are the data size
			TypeConverter.intToBytes(plainText.length, data, TypeConverter.INT_BYTE_SIZE);
			// Then copy the data buffer to encrypt
			ArrayUtils.copyByteArray(plainText, 0, data, TypeConverter.INT_BYTE_SIZE * 2, plainText.length);

			// Encrypt the data
			SymmetricCipher.encryptComplete(data, (short) 0, (short) dataSize, response, (short) 0);

			// Return the encrypted data to the host application
			setResponse(response, 0, dataSize);

			DebugPrint.printString("Encrypted data:");
			DebugPrint.printBuffer(response);

			status = IntelApplet.APPLET_SUCCESS;
		}
		catch (Exception ex)
		{
			DebugPrint.printString("ERROR: failed to save data\n" + ex.getMessage());
		}
		
		return status;
	}
	
	private byte[] decrypt(byte[] cipherText) {
		// todo implement
		return cipherText;
	}
	


}
