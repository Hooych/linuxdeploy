package ru.meefik.linuxdeploy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.AssetManager;

public class ShellEnv {

	private Context c;

	public ShellEnv(Context c) {
		this.c = c;
		PrefStore.get(c);
	}
	
	private void sendLogs(final String msg) {
		if (MainActivity.handler != null) {
			MainActivity.handler.post(new Runnable() {
				@Override
				public void run() {
					MainActivity.printLogMsg(msg);
				}
			});
		}
	}
	
	private boolean isRooted() {
		// exec linuxdeploy command
		List<String> params = new ArrayList<String>();
		params.add("su");
		params.add("ls /data/local 1>/dev/null");
		ExecCmd ex = new ExecCmd(params);
		ex.run();
		if (ex.status) {
			return true;
		} else {
			sendLogs("Require superuser privileges (root)!\n");
			return false;
		}
	}

	private boolean copyFile(String homeDir, String filename) {
		boolean result = true;
		AssetManager assetManager = c.getAssets();
		InputStream in = null;
		OutputStream out = null;
		try {
			in = assetManager.open(filename);
			String newFileName = homeDir
					+ filename.replaceFirst(PrefStore.ROOT_ASSETS, "");
			// Log.d("linuxdeploy", "extract: "+filename+" to "+newFileName);
			out = new FileOutputStream(newFileName);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;
		} catch (IOException e) {
			e.printStackTrace();
			result = false;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	private boolean copyFileOrDir(String homeDir, String path) {
		AssetManager assetManager = c.getAssets();
		String assets[] = null;
		try {
			assets = assetManager.list(path);
			if (assets.length == 0) {
				if (!copyFile(homeDir, path))
					return false;
			} else {
				String fullPath = homeDir
						+ path.replaceFirst(PrefStore.ROOT_ASSETS, "");
				// String fullPath =
				// getFilesDir().getAbsolutePath()+File.separator+path;
				File dir = new File(fullPath);
				if (!dir.exists()) {
					dir.mkdir();
					// Log.d("linuxdeploy", "mkdir: "+fullPath);
				}

				for (int i = 0; i < assets.length; ++i) {
					if (!copyFileOrDir(homeDir, path + "/" + assets[i]))
						return false;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void updateEnv() {
		if (!isRooted()) return;
		
		sendLogs("Updating environment ... ");

		if (PrefStore.ENV_DIR.length() == 0) {
			sendLogs("fail\n");
			return;
		}

		List<String> params = new ArrayList<String>();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		if (!PrefStore.DEBUG_MODE.equals("y")) {
			params.add("exec 1>/dev/null");
			params.add("exec 2>/dev/null");
		}
		params.add("mkdir " + PrefStore.ENV_DIR);
		params.add("rm -R " + PrefStore.ENV_DIR + "/bin");
		params.add("rm -R " + PrefStore.ENV_DIR + "/etc");
		params.add("rm -R " + PrefStore.ENV_DIR + "/deploy");
		params.add("chmod 777 " + PrefStore.ENV_DIR);
		params.add("exit");
		ExecCmd ex = new ExecCmd(params);
		ex.run();
		if (!ex.status) {
			sendLogs("fail\n");
			return;
		}

		boolean copyResult = copyFileOrDir(PrefStore.ENV_DIR,
				PrefStore.ROOT_ASSETS);
		if (!copyResult) {
			sendLogs("fail\n");
			return;
		}

		params.clear();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		if (!PrefStore.DEBUG_MODE.equals("y")) {
			params.add("exec 1>/dev/null");
			params.add("exec 2>/dev/null");
		}
		params.add("chmod 755 " + PrefStore.ENV_DIR);
		params.add("chmod 755 " + PrefStore.ENV_DIR + "/bin");
		params.add("chmod 755 " + PrefStore.ENV_DIR + "/bin/busybox");
		params.add(PrefStore.ENV_DIR + "/bin/busybox --install -s "
				+ PrefStore.ENV_DIR + "/bin");
		params.add("PATH=" + PrefStore.ENV_DIR + "/bin:$PATH; export PATH");
		params.add("chmod -R 755 " + PrefStore.ENV_DIR + "/bin");
		params.add("chmod -R a+rX " + PrefStore.ENV_DIR + "/etc "
				+ PrefStore.ENV_DIR + "/deploy");
		params.add("chmod 755 " + PrefStore.ENV_DIR
				+ "/deploy/debootstrap/pkgdetails");
		params.add("chown -R root:root " + PrefStore.ENV_DIR + "/bin "
				+ PrefStore.ENV_DIR + "/etc " + PrefStore.ENV_DIR + "/deploy");
		if (PrefStore.SYMLINK) {
			params.add("rm -f /system/bin/linuxdeploy");
			params.add("ln -s "
					+ PrefStore.ENV_DIR
					+ "/bin/linuxdeploy /system/bin/linuxdeploy || "
					+ "{ mount -o rw,remount /system; rm -f /system/bin/linuxdeploy; ln -s "
					+ PrefStore.ENV_DIR
					+ "/bin/linuxdeploy /system/bin/linuxdeploy; mount -o ro,remount /system; }");
		}
		params.add("echo '" + PrefStore.VERSION + "' > " + PrefStore.ENV_DIR
				+ "/etc/version");
		params.add("chmod 644 " + PrefStore.ENV_DIR + "/etc/version");
		params.add("exit");
		ex = new ExecCmd(params);
		ex.run();
		if (!ex.status) {
			sendLogs("fail\n");
			return;
		}
		sendLogs("done\n");
	}

	public void updateConfig() {
		File f = new File(PrefStore.ENV_DIR + "/etc/deploy.conf");
		if (!f.exists())
			return;

		sendLogs("Updating configuration file ... ");

		List<String> params = new ArrayList<String>();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		if (!PrefStore.DEBUG_MODE.equals("y")) {
			params.add("exec 1>/dev/null");
			params.add("exec 2>/dev/null");
		}
		params.add("PATH=" + PrefStore.ENV_DIR + "/bin:$PATH; export PATH");
		params.add("cd " + PrefStore.ENV_DIR);
		params.add("sed -i 's|^ENV_DIR=.*|ENV_DIR=\"" + PrefStore.ENV_DIR
				+ "\"|g' " + PrefStore.ENV_DIR + "/bin/linuxdeploy");
		params.add("sed -i 's|^#!.*|#!" + PrefStore.ENV_DIR + "/bin/sh|g' "
				+ PrefStore.ENV_DIR + "/bin/linuxdeploy");
		params.add("sed -i 's|^DEBUG_MODE=.*|DEBUG_MODE=\""
				+ PrefStore.DEBUG_MODE + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^TRACE_MODE=.*|TRACE_MODE=\""
				+ PrefStore.TRACE_MODE + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^IMG_TARGET=.*|IMG_TARGET=\""
				+ PrefStore.IMG_TARGET + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^IMG_SIZE=.*|IMG_SIZE=\"" + PrefStore.IMG_SIZE
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^FS_TYPE=.*|FS_TYPE=\"" + PrefStore.FS_TYPE
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^DEPLOY_TYPE=.*|DEPLOY_TYPE=\""
				+ PrefStore.DEPLOY_TYPE + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^DISTRIB=.*|DISTRIB=\"" + PrefStore.DISTRIB
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^ARCH=.*|ARCH=\"" + PrefStore.ARCH + "\"|g' "
				+ PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^SUITE=.*|SUITE=\"" + PrefStore.SUITE + "\"|g' "
				+ PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^MIRROR=.*|MIRROR=\"" + PrefStore.MIRROR
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^USER_NAME=.*|USER_NAME=\"" + PrefStore.USER_NAME
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^SERVER_DNS=.*|SERVER_DNS=\""
				+ PrefStore.SERVER_DNS + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^LOCALE=.*|LOCALE=\"" + PrefStore.LOCALE
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^INSTALL_GUI=.*|INSTALL_GUI=\""
				+ PrefStore.INSTALL_GUI + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^DESKTOP_ENV=.*|DESKTOP_ENV=\""
				+ PrefStore.DESKTOP_ENV + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^CUSTOM_STARTUP=.*|CUSTOM_STARTUP=\""
				+ PrefStore.CUSTOM_STARTUP + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^CUSTOM_MOUNT=.*|CUSTOM_MOUNT=\""
				+ PrefStore.CUSTOM_MOUNT + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^SSH_START=.*|SSH_START=\"" + PrefStore.SSH_START
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^SSH_PORT=.*|SSH_PORT=\"" + PrefStore.SSH_PORT
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_START=.*|VNC_START=\"" + PrefStore.VNC_START
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_DISPLAY=.*|VNC_DISPLAY=\""
				+ PrefStore.VNC_DISPLAY + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_DEPTH=.*|VNC_DEPTH=\"" + PrefStore.VNC_DEPTH
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_DPI=.*|VNC_DPI=\"" + PrefStore.VNC_DPI
				+ "\"|g' " + PrefStore.ENV_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_GEOMETRY=.*|VNC_GEOMETRY=\""
				+ PrefStore.VNC_GEOMETRY + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^XSERVER_START=.*|XSERVER_START=\""
				+ PrefStore.XSERVER_START + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^XSERVER_DISPLAY=.*|XSERVER_DISPLAY=\""
				+ PrefStore.XSERVER_DISPLAY + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^XSERVER_HOST=.*|XSERVER_HOST=\""
				+ PrefStore.XSERVER_HOST + "\"|g' " + PrefStore.ENV_DIR
				+ "/etc/deploy.conf");
		params.add("[ $? -eq 0 ] && exit 0 || exit 1");

		ExecCmd ex = new ExecCmd(params);
		ex.run();
		if (!ex.status) {
			sendLogs("fail\n");
			return;
		}
		sendLogs("done\n");
	}

	public void deployCmd(String cmd) {
		if (!isRooted()) return;
		// check for update env
		boolean update = true;
		File f = new File(PrefStore.ENV_DIR + "/etc/version");
		if (f.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(f));
				try {
					String line = br.readLine();
					if (PrefStore.VERSION.equals(line))
						update = false;
				} finally {
					br.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (update) {
			updateEnv();
			updateConfig();
			//sendLogs("Need to update the operating environment!\nTry Menu -> Settings -> Update ENV\n");
			//return;
		}
		// exec linuxdeploy command
		List<String> params = new ArrayList<String>();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add(PrefStore.ENV_DIR + "/bin/linuxdeploy " + cmd);
		params.add("exit");
		new ExecCmd(params).run();
	}
	
}
