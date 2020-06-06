/**
 * 
 */
package Notes;
import com.intel.crypto.*;
import com.intel.langutil.ArrayUtils;
import com.intel.langutil.TypeConverter;
import com.intel.util.DebugPrint;
/**
 * @author david
 *
 */
public final class Crypto {
	public static byte[] encrypt(byte[] plainText) {
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
				
		DebugPrint.printString("hash data:");
		DebugPrint.printBuffer(hash);
		
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
		
		DebugPrint.printString("returnArray data:");
		DebugPrint.printBuffer(returnArray);
		
		// Return the encrypted data to the host application
		return returnArray;
	}

	public static byte[] decrypt(byte[] cipherText) {
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
		
		if(hashSize != oldHashSize || ArrayUtils.compareByteArray(oldHash, indexZero, hash, indexZero, hashSize)) throw new CryptoException("Hash not match");
		
		DebugPrint.printString("hash data:");
		DebugPrint.printBuffer(hash);
		
		DebugPrint.printString("data:");
		DebugPrint.printBuffer(plainText);
		return plainText;
	}
}

