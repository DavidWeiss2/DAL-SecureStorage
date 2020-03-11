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
                    existFiles = new List<UInt32>();
                    foreach (var fileP in Directory.GetFiles(dirPath))
                        if (Path.GetFileName(fileP
                    {

                    }
                    // todo go over the content of dirPath and add the fileNames (only numbers) to the list
                }
                return existFiles;
            }
            private set { existFiles = null; }
        }


        public void SendAndRecv2(JhiSession Session, int nCommandId, byte[] InBuf, ref byte[] OutBuf, out int ResponseCode, bool enableWrite = false, UInt32[] filesNameToSend = null)
        {
            InBuf = insertFSInfoToBuffer(InBuf, enableWrite, filesNameToSend);
            jhi.SendAndRecv2(Session, nCommandId, InBuf, ref OutBuf, out ResponseCode);
            OutBuf = HandleReturnadData();
        }

        private byte[] insertFSInfoToBuffer(byte[] inBuf, bool enableWrite, UInt32[] filesNameToSend)
        {
            byte[] result;
            if (enableWrite || filesNameToSend != null)  // enableWrite, send FS.
            {
                List<UInt32> fs = ExistFiles;
                List<KeyValuePair<UInt32, byte[]>> filesToSend = loadFiles(filesNameToSend);
                //todo: Find out if it's ok to convert int and long to Uint32.
                UInt32 totalFilesLen = (UInt32)filesToSend.Sum(p => (UInt32)p.Value.Length);

                result = new byte[9 + ExistFiles.Count + totalFilesLen + filesNameToSend.Length * 8 + inBuf.Length];
                result[0] = (byte)1;
                UintToByteArray((UInt32)ExistFiles.Count).CopyTo(result, 1);
                UintToByteArray((UInt32)filesNameToSend.Length).CopyTo(result, 5);
                UInt32 offset = 9;
                foreach (var file in filesToSend)
                {
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
            return result;
        }


        private List<KeyValuePair<UInt32, byte[]>> loadFiles(UInt32[] filesNameToSend)
        {
            List<KeyValuePair<UInt32, byte[]>> files = new List<KeyValuePair<uint, byte[]>>();  // name:file
            foreach (int fileName in filesNameToSend)
            {
                string filePath = Path.Combine(dirPath, fileName.ToString());
                //todo load the file and add the three (name, length, data) to the 
                //todo add its len to totalLength
            }
            return files;
        }

        private byte[] HandleReturnadData()
        {
            // todo
            throw new NotImplementedException();
        }

        private void deleteFiles(params UInt32[] fileNames)
        {
            foreach (var fileName in fileNames)
                File.Delete(Path.Combine(this.dirPath, fileName.ToString()));
        }

        private void writeFile(UInt32 fileName, byte[] data)
        {
            File.WriteAllBytes(Path.Combine(this.dirPath, fileName.ToString()), data);
        }

        private byte[] readFile(UInt32 fileName)
        {
            return File.ReadAllBytes(Path.Combine(this.dirPath, fileName.ToString()));
        }




        private byte[] UintToByteArray(UInt32 value) // big endian
        {
            return new byte[] { (byte)(value >> 0x18), (byte)(value >> 0x10), (byte)(value >> 8), (byte)value };
        }

        private int ByteArrayToInt(byte[] bytes) // big endian
        {
            return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               ((bytes[3] & 0xFF) << 0);
        }
    }
}
