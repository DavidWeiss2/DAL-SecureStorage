using System;
using System.Text;
using Intel.Dal;
using System.Diagnostics;
using System.Linq;

namespace Notes
{
    class Program
    {
        static void Main(string[] args)
        {
#if AMULET
            // When compiled for Amulet the Jhi.DisableDllValidation flag is set to true 
			// in order to load the JHI.dll without DLL verification.
            // This is done because the JHI.dll is not in the regular JHI installation folder, 
			// and therefore will not be found by the JhiSharp.dll.
            // After disabling the .dll validation, the JHI.dll will be loaded using the Windows search path
			// and not by the JhiSharp.dll (see http://msdn.microsoft.com/en-us/library/7d83bc18(v=vs.100).aspx for 
			// details on the search path that is used by Windows to locate a DLL) 
            // In this case the JHI.dll will be loaded from the $(OutDir) folder (bin\Amulet by default),
			// which is the directory where the executable module for the current process is located.
            // The JHI.dll was placed in the bin\Amulet folder during project build.
            Jhi.DisableDllValidation = true;
#endif

            Jhi jhi = Jhi.Instance;
            JhiSession session;

            SecureStorage secureStorage = new SecureStorage("C:\\Users\\" + Environment.UserName + "\\source\\repos\\DAL-SecureStorage\\encryptedFiles");
            
            // This is the UUID of this Trusted Application (TA).
            //The UUID is the same value as the applet.id field in the Intel(R) DAL Trusted Application manifest.
            string appletID = "64146226-a01b-4bec-a6cc-6056cee6f093";
            // This is the path to the Intel Intel(R) DAL Trusted Application .dalp file that was created by the Intel(R) DAL Eclipse plug-in.
            string appletPath = "C:\\Users\\" + Environment.UserName + "\\source\\repos\\DAL-SecureStorage\\applet\\Notes\\bin\\Notes.dalp";
            //string appletPath = "C:\\Users\\frankela\\source\\repos\\DAL-SecureStorage\\applet\\Notes\\bin\\Notes.dalp";

            // Install the Trusted Application
            Console.WriteLine("Installing the applet.");
            try
            {
                jhi.Install(appletID, appletPath);
            }
            catch (Intel.Dal.JhiException)
            {
                appletPath = "C:\\Users\\" + Environment.UserName + "\\source\\repos\\DAL-SecureStorage\\applet\\Notes\\bin\\Notes-debug.dalp";
                jhi.Install(appletID, appletPath);
            }

            // Start a session with the Trusted Application
            byte[] initBuffer = new byte[] { }; // Data to send to the applet onInit function
            Console.WriteLine("Opening a session.");
            jhi.CreateSession(appletID, JHI_SESSION_FLAGS.None, initBuffer, out session);

            int readCMD = 0, writeCMD = 1, seyHiCMD = 2;
            byte[] sendBuff = new byte[200];
            byte[] recvBuff = new byte[200]; // A buffer to hold the output data from the TA
            int responseCode;

            #region Hi
            //secureStorage.SendAndRecv2(session, seyHiCMD, sendBuff, ref recvBuff, out responseCode);
            //Console.WriteLine(recvBuff);
            #endregion

            secureStorage.deleteFiles(secureStorage.ExistFiles.ToArray<uint>());


            #region readWrite_testCase
            // write to file 42 "Hello"
            sendBuff = new byte[16];
            recvBuff = new byte[250]; // A buffer to hold the output data from the TA
            byte[] hello = UTF32Encoding.UTF8.GetBytes("Hello world!");
            UintToByteArray(42).CopyTo(sendBuff, 0);
            hello.CopyTo(sendBuff, 4);
            secureStorage.SendAndRecv2(session, writeCMD, sendBuff, ref recvBuff, out responseCode, true);


            // read from file 42, check that it is "Hello".
            recvBuff = new byte[250];
            sendBuff = UintToByteArray(42);
            secureStorage.SendAndRecv2(session, readCMD, sendBuff, ref recvBuff, out responseCode, true, 42);

            // check if they are equals.
            Debug.Assert(recvBuff.SequenceEqual(hello));
            #endregion


            #region The Original Example
            //// Send and Receive data to/from the Trusted Application
            //byte[] sendBuff = UTF32Encoding.UTF8.GetBytes("Hello"); // A message to send to the TA
            //byte[] recvBuff = new byte[6]; // A buffer to hold the output data from the TA
            //int responseCode; // The return value that the TA provides using the IntelApplet.setResponseCode method
            //int cmdId = 1; // The ID of the command to be performed by the TA
            //Console.WriteLine("Performing send and receive operation.");
            //jhi.SendAndRecv2(session, cmdId, sendBuff, ref recvBuff, out responseCode);
            //Console.Out.WriteLine("Response buffer is " + UTF32Encoding.UTF8.GetString(recvBuff));
            #endregion

            // Close the session
            Console.WriteLine("Closing the session.");
            jhi.CloseSession(session);

            //Uninstall the Trusted Application
            Console.WriteLine("Uninstalling the applet.");
            jhi.Uninstall(appletID);

            Console.WriteLine("Press Enter to finish.");
            Console.Read();
        }

        private static byte[] UintToByteArray(UInt32 value) // big endian
        {
            return new byte[] { (byte)(value >> 0x18), (byte)(value >> 0x10), (byte)(value >> 8), (byte)value };
        }

        private static int ByteArrayToInt(byte[] bytes) // big endian
        {
            return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               ((bytes[3] & 0xFF) << 0);
        }

    }
}