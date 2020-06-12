package Notes;

import com.intel.util.*;
import com.intel.crypto.CryptoException;
import com.intel.crypto.HashAlg;
import com.intel.crypto.Random;
import com.intel.crypto.SymmetricBlockCipherAlg;
import com.intel.langutil.*;
import java.util.Hashtable;
import java.util.*; 

public class SecureStorage {
	Hashtable<Integer, Boolean> allFilesNames;  // the original FS (list of file names).
	Hashtable<Integer, byte[]> files;  // the loaded files.
	
	Hashtable<Integer, Boolean> newFiles = new Hashtable<Integer, Boolean>(); // list of modified files that where not in the original FS.
	Hashtable<Integer, Boolean> filesToDelete = new Hashtable<Integer, Boolean>(); // list of deleted files.
	Hashtable<Integer, Boolean> modifiedFiles = new Hashtable<Integer, Boolean>(); // list of modified files.


	public byte[] extractFSInfoFromBuffer(byte[] request) {
		byte[] userRequest;
		if (request[0] == (byte)1) // there is a FS to fetch (enableWrite).
		{
			int FS_size = TypeConverter.bytesToInt(request, 1);  // number of existingFiles
			allFilesNames = new Hashtable<Integer, Boolean>(FS_size);
			int importedFilesNum = TypeConverter.bytesToInt(request, 5);
			int offset = 9;
			for (; offset < 9 + 4 * FS_size; offset += 4)
				allFilesNames.put(TypeConverter.bytesToInt(request, offset), true);

			files = new Hashtable<Integer, byte[]>(importedFilesNum);
			for (int i = 0; i < importedFilesNum; i++) {
				int fileName = TypeConverter.bytesToInt(request, offset);
				int fileLen = TypeConverter.bytesToInt(request, offset + 4);
				byte[] file = new byte[fileLen];
				ArrayUtils.copyByteArray(request, offset + 8, file, 0, fileLen);
				files.put(fileName, file);
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
		//DebugPrint.printString("exiting extractFSInfoFromBuffer()." );
		return userRequest;
	}
	
	public byte[] insertFSInfoToBuffer(byte[] response) {
		//DebugPrint.printString("In insertFSInfoToBuffer");
		//calculate buffer size
		int filesToDelete_n = filesToDelete.size();
		int modifiedFiles_n = modifiedFiles.size();
		int bufferSize = 8 + filesToDelete_n * 4 + 8 * modifiedFiles_n + response.length;
		Enumeration<Integer> modifiedFiles_list = modifiedFiles.keys();
		while (modifiedFiles_list.hasMoreElements())
			bufferSize += files.get(modifiedFiles_list.nextElement()).length;
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
			//DebugPrint.printString("offset: " + offset);
			int fileName = modifiedFiles_list.nextElement();
			byte[] file = files.get(fileName);
			int fileLen = file.length;
			TypeConverter.intToBytes(fileName, result, offset);
			TypeConverter.intToBytes(fileLen, result, offset + 4);
			//DebugPrint.printString("In SS insertFSInfoToBuffer(). "
			//		+ "reading modified files. fileName = " + fileName + ", file = " + new String(file));  //TODO delete

			ArrayUtils.copyByteArray(file, 0, result, offset + 8, fileLen);
			offset += (8 + fileLen);
		}
		// the user buffer
		ArrayUtils.copyByteArray(response, 0, result, offset, response.length);
		//DebugPrint.printString("exiting insertFSInfoToBuffer()." );
		return	result;
	}
	
	public byte[] read(int fileName) {
		//DebugPrint.printString("read() in SS");
		//DebugPrint.printString("allFilesNames.size() = " + allFilesNames.size());
		if (!allFilesNames.containsKey(fileName))
			throw new IOException("The file " + fileName + " doesn't exists.");
		else if (!files.containsKey(fileName))
			throw new IOException("The file " + fileName + " doesn't loaded.");
		//DebugPrint.printString("exiting SS read()." );
		return decrypt(files.get(fileName));
	}
	
	public void write(int fileName, byte[] file) {
		//DebugPrint.printString("write() in SS");
		//DebugPrint.printString("file.length = " + file.length);
		//DebugPrint.printString("file = " + new String(file));
		if (allFilesNames.containsKey(fileName))
			throw new IOException("The file " + fileName + " already exists.");
		else if (!filesToDelete.containsKey(fileName))
			newFiles.put(fileName, true);
			
		files.put(fileName, encrypt(file));
		allFilesNames.put(fileName, true);
		modifiedFiles.put(fileName, true);
	}
	
	public void delete(int fileName) {
		if (!allFilesNames.containsKey(fileName))
			throw new IOException("The file " + String.valueOf(fileName) + " doesn't exists.");
		
		if (!newFiles.containsKey(fileName))
			filesToDelete.put(fileName, true);
		
		allFilesNames.remove(fileName);
		files.remove(fileName);
		modifiedFiles.remove(fileName);
	}
	
	
	static byte[] encrypt(byte[] plainText) {
		//DebugPrint.printString("on Crypto.encrypt()");
		//Java is PITA 
		short indexZero = (short)0;
		
		// Create Platform Binded cipher
		SymmetricBlockCipherAlg SymmetricCipher = SymmetricBlockCipherAlg.create(SymmetricBlockCipherAlg.ALG_TYPE_PBIND_AES_256_CBC);
		
		//Create and Initial the Hash(plainText)
		HashAlg hashAlg = HashAlg.create(HashAlg.HASH_TYPE_SHA256);
		short hashSize = hashAlg.getHashLength();
		byte[] hash =new byte[hashSize];
		//init
		hashAlg.processComplete(plainText,indexZero,(short)plainText.length,hash,indexZero);
				
		//DebugPrint.printString("hash data:");
		//DebugPrint.printBuffer(hash);
		
		//constart a byte array to hold the data to encrypt (Hash size | Hash | data size | data | Padded if neccesry to match bloack size)
		int dataSize = TypeConverter.SHORT_BYTE_SIZE + hashSize + TypeConverter.INT_BYTE_SIZE + plainText.length;
		// Align the data size to block buffer size
		short blockSize = SymmetricCipher.getBlockSize();
		if (dataSize % blockSize != 0)
			dataSize = dataSize + blockSize - (dataSize % blockSize);
		// An array to hold the data to encrypt
		byte[] data = new byte[dataSize];
		//init the array
		int currentIndexInsideTheDataArray = indexZero;
		// First bytes are the hashSize value
		TypeConverter.shortToBytes(hashSize, data, currentIndexInsideTheDataArray);
		currentIndexInsideTheDataArray += TypeConverter.SHORT_BYTE_SIZE;
		// Second bytes are the hash
		ArrayUtils.copyByteArray(hash, 0, data, currentIndexInsideTheDataArray, hashSize);
		currentIndexInsideTheDataArray += hashSize;
		// next bytes are the plainText length value
		TypeConverter.intToBytes(plainText.length, data, currentIndexInsideTheDataArray);
		currentIndexInsideTheDataArray += TypeConverter.INT_BYTE_SIZE;
		// Then copy the plainText buffer to encrypt
		ArrayUtils.copyByteArray(plainText, 0, data, currentIndexInsideTheDataArray, plainText.length);

		// An array for the encrypted data, 
		// Data size stays the same after encryption because we are using a symmetric key
		byte[] encryptedData = new byte[dataSize];
		
		//Create and Initial the Initialization Vector for AES 256 CBC mode.
		short ivSize = SymmetricCipher.getBlockSize();
		byte[] iv = new byte[ivSize];
		Random.getRandomBytes(iv, indexZero, ivSize);
		//init
		SymmetricCipher.setIV(iv, indexZero, ivSize);
		
		// Encrypt the data
		SymmetricCipher.encryptComplete(data, indexZero, (short) dataSize, encryptedData, indexZero);
		
		// returnArray is IV | encryptedData
		byte[] returnArray = new byte[ivSize+dataSize];
		ArrayUtils.copyByteArray(iv, indexZero, returnArray, indexZero, ivSize);
		ArrayUtils.copyByteArray(encryptedData, indexZero, returnArray, ivSize, dataSize);
		
		//DebugPrint.printString("returnArray data:");
		//DebugPrint.printBuffer(returnArray);
		
		// Return the encrypted data to the host application
		return returnArray;
	}

	static byte[] decrypt(byte[] cipherText) {
		//Java is PITA 
		short indexZero = (short)0;
		
		// Create Platform Binded cipher
		SymmetricBlockCipherAlg SymmetricCipher = SymmetricBlockCipherAlg.create(SymmetricBlockCipherAlg.ALG_TYPE_PBIND_AES_256_CBC);

		//Create and Initial the Initialization Vector for AES 256 CBC mode using the chipher text firsts bytes.
		short ivSize = SymmetricCipher.getBlockSize();
		byte[] iv = new byte[ivSize];
		ArrayUtils.copyByteArray(cipherText, indexZero, iv, indexZero, ivSize);
		//init
		SymmetricCipher.setIV(iv, indexZero, ivSize);

		// An array for the decrypted data, 
		// Data size stays is cipherText length minus the iv bytes in the start of the chperText
		// decrypt data is: Hash size | Hash | data size | data | paded
		byte[] decryptedData = new byte[cipherText.length - ivSize];		
		
		// Decrypt the data
		SymmetricCipher.decryptComplete(cipherText, ivSize, (short) (cipherText.length - ivSize), decryptedData, indexZero);
		
		int currentIndexInsideTheDecryptedDataArray = indexZero;
		
		//old hash:
		short oldHashSize = TypeConverter.bytesToShort(decryptedData, currentIndexInsideTheDecryptedDataArray);
		currentIndexInsideTheDecryptedDataArray += TypeConverter.SHORT_BYTE_SIZE;
		byte[] oldHash = new byte[oldHashSize];
		ArrayUtils.copyByteArray(decryptedData, currentIndexInsideTheDecryptedDataArray, oldHash, indexZero, oldHashSize);
		currentIndexInsideTheDecryptedDataArray += oldHashSize;
		
		//old data:
		int decryptDataSize = TypeConverter.bytesToInt(decryptedData, currentIndexInsideTheDecryptedDataArray);
		currentIndexInsideTheDecryptedDataArray += TypeConverter.INT_BYTE_SIZE;
		byte[] plainText = new byte[decryptDataSize];
		ArrayUtils.copyByteArray(decryptedData, currentIndexInsideTheDecryptedDataArray, plainText, indexZero, decryptDataSize);
		
		HashAlg hashAlg = HashAlg.create(HashAlg.HASH_TYPE_SHA256);
		short hashSize = hashAlg.getHashLength();
		byte[] hash =new byte[hashSize];
		//init
		hashAlg.processComplete(plainText,indexZero,(short)plainText.length,hash,indexZero);
		
		if(hashSize != oldHashSize || !ArrayUtils.compareByteArray(oldHash, indexZero, hash, indexZero, hashSize)) {
			//DebugPrint.printString("Hash not match");
			throw new CryptoException("Hash not match");
		}
		
		//DebugPrint.printString("hash data:");
		//DebugPrint.printBuffer(hash);
		
		//DebugPrint.printString("data:");
		//DebugPrint.printBuffer(plainText);
		return plainText;
	}



}
