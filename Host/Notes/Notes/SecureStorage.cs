using Intel.Dal;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace Notes
{
    class SecureStorage
    {
        Jhi jhi = Jhi.Instance;

        public string dirPath { get; private set; }

        public SecureStorage(string dirPath = null)
        {
            if (dirPath != null)
                this.dirPath = dirPath;
            else  //todo defule path (if needed?)
            { }
        }

        private List<UInt32> existFiles = null;
        public List<UInt32> ExistFiles
        {
            get
            {
                if (existFiles == null)
                {
                    // go over the content of dirPath and add the fileNames (only numbers) to the list
                    string[] files = Directory.GetFiles(dirPath);
                    existFiles = new List<UInt32>(files.Length);
                    UInt32 fileName;
                    foreach (var fileP in files)
                        if (UInt32.TryParse(Path.GetFileName(fileP), out fileName))
                            existFiles.Add(fileName);
                }
                return existFiles;
            }
            private set { existFiles = null; }
        }


        public void SendAndRecv2(JhiSession Session, int nCommandId, byte[] InBuf, ref byte[] OutBuf, out int ResponseCode, bool enableWrite = false, params UInt32[] filesNameToSend)
        {
            InBuf = insertFSInfoToBuffer(InBuf, enableWrite, filesNameToSend);
            jhi.SendAndRecv2(Session, nCommandId, InBuf, ref OutBuf, out ResponseCode);
            OutBuf = HandleReturnedData(OutBuf);
        }

        private byte[] insertFSInfoToBuffer(byte[] inBuf, bool enableWrite, UInt32[] filesNameToSend)
        {
            byte[] result;
            if (enableWrite || filesNameToSend.Count() != 0)  // enableWrite, send FS.
            {
                List<UInt32> fs = ExistFiles;
                List<KeyValuePair<UInt32, byte[]>> filesToSend = loadFiles(filesNameToSend);
                //todo: Find out if it's ok to convert int and long to Uint32.
                UInt32 totalFilesLen = (UInt32)filesToSend.Sum(p => (UInt32)p.Value.Length);

                result = new byte[9 + ExistFiles.Count * 4 + totalFilesLen + filesNameToSend.Length * 8 + inBuf.Length];
                result[0] = (byte)1;
                UintToByteArray((UInt32)ExistFiles.Count).CopyTo(result, 1);
                UintToByteArray((UInt32)filesNameToSend.Length).CopyTo(result, 5);
                UInt32 offset = 9;

                foreach (var fileName in fs)  // Add the file system list.
                {
                    UintToByteArray(fileName).CopyTo(result, offset);
                    offset += 4;
                }

                foreach (var file in filesToSend)
                {  // add the three (name, length, data)
                    UintToByteArray(file.Key).CopyTo(result, offset);
                    UintToByteArray((UInt32)file.Value.Length).CopyTo(result, offset + 4);
                    file.Value.CopyTo(result, offset + 8);
                    offset += (8 + (UInt32)file.Value.Length);
                }
                inBuf.CopyTo(result, offset);
            }
            else  //send only the user buffer.
            {
                result = new byte[1 + inBuf.Length];
                result[0] = (byte)0;
                inBuf.CopyTo(result, 1);
            }
            ExistFiles = null;
            return result;
        }


        private List<KeyValuePair<UInt32, byte[]>> loadFiles(UInt32[] filesNameToSend)
        {
            List<KeyValuePair<UInt32, byte[]>> files = new List<KeyValuePair<uint, byte[]>>();  // <name:file>
            foreach (uint fileName in filesNameToSend)
            {
                string filePath = Path.Combine(dirPath, fileName.ToString());
                byte[] file = File.ReadAllBytes(filePath);
                files.Add(new KeyValuePair<uint, byte[]>(fileName, file));
            }
            return files;
        }

        private byte[] HandleReturnedData(byte[] bufferFromDal)
        {
            int filesToDelete_n = ByteArrayToInt(bufferFromDal);
            int modifiedFiles_n = ByteArrayToInt(bufferFromDal, 4);
            int offset;

            //delete files
            for (offset = 8; offset < 8 + 4 * filesToDelete_n; offset += 4)
                deleteFiles((UInt32)ByteArrayToInt(bufferFromDal, offset));

            for (int i = 0; i < modifiedFiles_n; i++)  // Modified files
            {
                int fileName = ByteArrayToInt(bufferFromDal, offset);
                int fileLen = ByteArrayToInt(bufferFromDal, offset + 4);
                writeFile((UInt32)fileName, bufferFromDal, offset + 8, fileLen);
                offset += (8 + fileLen);
            }

            //receive the user buffer.
            int userBuffLen = bufferFromDal.Length - offset;
            byte[] userBuff = new byte[userBuffLen];
            Array.Copy(bufferFromDal, offset, userBuff, 0, userBuffLen);
            return userBuff;
        }

        private void deleteFiles(params UInt32[] fileNames)
        {
            foreach (var fileName in fileNames)
                File.Delete(Path.Combine(this.dirPath, fileName.ToString()));
        }

        private void writeFile(UInt32 fileName, byte[] data, int offset = 0, int? count = null)
        {
            if (count == null)
                File.WriteAllBytes(Path.Combine(this.dirPath, fileName.ToString()), data);
            else
                using (FileStream fs = File.Create(Path.Combine(this.dirPath, fileName.ToString())))
                    fs.Write(data, offset, (int)count);
        }

        private byte[] readFile(UInt32 fileName)
        {
            return File.ReadAllBytes(Path.Combine(this.dirPath, fileName.ToString()));
        }


        private byte[] UintToByteArray(UInt32 value) // big endian
        {
            return new byte[] { (byte)(value >> 0x18), (byte)(value >> 0x10), (byte)(value >> 8), (byte)value };
        }

        private int ByteArrayToInt(byte[] bytes, int offset = 0) // big endian
        {
            return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               ((bytes[offset + 3] & 0xFF) << 0);
        }
    }
}
