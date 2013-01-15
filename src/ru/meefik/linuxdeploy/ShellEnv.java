package ru.meefik.linuxdeploy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;

public class ShellEnv {

	private Context c;

	public ShellEnv(Context c) {
		this.c = c;
		PrefStore.get(c);
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

	private void sendLogs(final String msg) {
		MainActivity.handler.post(new Runnable() {
			@Override
			public void run() {
				MainActivity.printLogMsg(msg);
			}
		});
	}

	public void sysInfo() {
		List<String> params = new ArrayList<String>();
		params.add("sh");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add("PATH=" + PrefStore.HOME_DIR + "/bin:$PATH; export PATH");
		params.add("MNT_TARGET=" + PrefStore.HOME_DIR + "/mnt");
		if (PrefStore.DEBUG_MODE.equals("y"))
			params.add("cat " + PrefStore.HOME_DIR
					+ "/etc/deploy.conf | grep -v ^#");
		params.add("echo '[PRINT_LN] =================='");
		params.add("echo '[PRINT_LN] SYSTEM INFORMATION'");
		params.add("echo '[PRINT_LN] =================='");
		params.add("echo '[PRINT_LN] Application version: "
				+ PrefStore.VERSION_NAME + " (" + PrefStore.VERSION_CODE + ")'");
		params.add("echo '[PRINT_LN] Device: '$(getprop ro.product.brand) $(getprop ro.product.device)");
		params.add("echo '[PRINT_LN] CPU: '$(cat /proc/cpuinfo | grep ^Processor | awk -F': ' '{print $2}')");
		params.add("echo '[PRINT_LN] Android version: '$(getprop ro.build.version.release)");
		params.add("echo '[PRINT_LN] '$(cat /proc/meminfo | grep ^MemTotal)");
		params.add("echo '[PRINT_LN] '$(cat /proc/meminfo | grep ^SwapTotal)");
		params.add("echo '[PRINT] Support loop device: '");
		params.add("is_loop=`ls /dev/block/loop0`; [ -n \"$is_loop\" ] && echo '[RESULT_LN] yes' || echo '[RESULT_LN] no'");
		params.add("echo '[PRINT] Supported file systems: '");
		params.add("echo '[RESULT] '$(cat /proc/filesystems | grep -v nodev | sort | xargs)");
		params.add("echo '[RESULT_LN] '");
		params.add("LINUX_VERSION=`(. $MNT_TARGET/etc/os-release && echo $NAME) || echo unknown`");
		params.add("echo '[PRINT_LN] Active Linux system: '$LINUX_VERSION");
		params.add("echo '[PRINT_LN] Running services: '");
		params.add("echo '[PRINT] SSH server: '");
		params.add("is_ssh=`ps | grep '/usr/sbin/sshd' | grep -v grep`");
		params.add("[ -n \"$is_ssh\" ] && echo '[RESULT_LN] yes' || echo '[RESULT_LN] no'");
		params.add("echo '[PRINT] VNC server: '");
		params.add("is_ssh=`ps | grep 'Xtightvnc' | grep -v grep`");
		params.add("[ -n \"$is_ssh\" ] && echo '[RESULT_LN] yes' || echo '[RESULT_LN] no'");
		params.add("echo '[PRINT_LN] Mounted parts on Linux: '");
		params.add("for i in `cat /proc/mounts | grep $MNT_TARGET | awk '{print $2}' | sed \"s|$MNT_TARGET/*|/|g\"`; "
				+ "do echo \"[PRINT_LN] * $i\"; is_mounted=1; done");
		params.add("[ -z \"$is_mounted\" ] && echo '[PRINT_LN] ...not mounted anything'");
		params.add("exit");
		new ExecCmd(params).run();

		params.clear();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add("PATH=" + PrefStore.HOME_DIR + "/bin:$PATH; export PATH");
		params.add("MNT_TARGET=" + PrefStore.HOME_DIR + "/mnt");
		params.add("echo '[PRINT_LN] Available mount points:'");
		params.add("MOUNTS=`cat /proc/mounts | grep ^/dev/ | grep -v $MNT_TARGET | grep -v ' /mnt/asec/' | grep -v ' /mnt/secure/' | awk '{print $2\":\"$3}'`");
		params.add("for p in $MOUNTS; do PART=`echo $p | awk -F: '{print $1}'`; FSTYPE=`echo $p | awk -F: '{print $2}'`; "
				+ "stat -f $PART | grep ^Block | tr -d '\n' | awk '{avail=sprintf(\"%.1f\",$10*$3/1024/1024/1024);"
				+ "total=sprintf(\"%.1f\",$6*$3/1024/1024/1024);print \"[PRINT_LN] '$PART': \"avail\"/\"total\" GB ('$FSTYPE')\"}'; done");
		params.add("echo '[PRINT_LN] Available partitions: '");
		params.add("for i in /sys/block/*/dev; do "
				+ "if [ -f $i ]; then "
				+ "DEVNAME=$(echo $i | sed -e 's@/dev@@' -e 's@.*/@@'); "
				+ "[ -e \"/dev/$DEVNAME\" ] && DEVPATH=/dev/$DEVNAME; "
				+ "[ -e \"/dev/block/$DEVNAME\" ] && DEVPATH=/dev/block/$DEVNAME; "
				+ "[ -n \"$DEVPATH\" ] && PARTS=`fdisk -l $DEVPATH | grep ^/dev/ | awk '{print $1}'`; "
				+ "for PART in $PARTS; do "
				+ "SIZE=`fdisk -l $PART | grep 'Disk.*bytes' | awk '{ sub(/,/,\"\"); print $3\" \"$4}'`; "
				+ "BOOT=`fdisk -l $DEVPATH | grep ^$PART | awk '{print $2}'`; "
				+ "[ \"$BOOT\" = \"*\" ] && TYPE=`fdisk -l $DEVPATH | grep ^$PART | awk '{str=$7; for (i=8;i<=11;i++) if ($i!=\"\") str=str\" \"$i; print str}'` || "
				+ "TYPE=`fdisk -l $DEVPATH | grep ^$PART | awk '{str=$6; for (i=7;i<=10;i++) if ($i!=\"\") str=str\" \"$i; print str}'`; "
				+ "echo \"[PRINT_LN] $PART: $SIZE ($TYPE)\"; "
				+ "is_partitions=1; " + "done; fi; done");
		params.add("[ -z \"$is_partitions\" ] && echo '[PRINT_LN] ...no available partitions'");
		params.add("exit");
		new ExecCmd(params).run();
	}

	public void updateConfig() {

		File f = new File(PrefStore.HOME_DIR + "/etc/deploy.conf");
		if (!f.exists())
			return;

		sendLogs("[PRINT] Updating configuration file ... ");

		List<String> params = new ArrayList<String>();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add("PATH=" + PrefStore.HOME_DIR + "/bin:$PATH; export PATH");
		params.add("cd " + PrefStore.HOME_DIR);
		params.add("sed -i 's|^ENV_DIR=.*|ENV_DIR=\"" + PrefStore.HOME_DIR
				+ "\"|g' " + PrefStore.HOME_DIR + "/bin/linuxdeploy");
		params.add("sed -i 's|^#!.*|#!" + PrefStore.HOME_DIR + "/bin/sh|g' "
				+ PrefStore.HOME_DIR + "/bin/linuxdeploy");
		params.add("sed -i 's|^DEBUG_MODE=.*|DEBUG_MODE=\""
				+ PrefStore.DEBUG_MODE + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^TRACE_MODE=.*|TRACE_MODE=\""
				+ PrefStore.TRACE_MODE + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^IMG_TARGET=.*|IMG_TARGET=\""
				+ PrefStore.IMG_TARGET + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^IMG_SIZE=.*|IMG_SIZE=\"" + PrefStore.IMG_SIZE
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^FS_TYPE=.*|FS_TYPE=\"" + PrefStore.FS_TYPE
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^DEPLOY_TYPE=.*|DEPLOY_TYPE=\""
				+ PrefStore.DEPLOY_TYPE + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^DISTRIB=.*|DISTRIB=\"" + PrefStore.DISTRIB
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^ARCH=.*|ARCH=\"" + PrefStore.ARCH + "\"|g' "
				+ PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^SUITE=.*|SUITE=\"" + PrefStore.SUITE + "\"|g' "
				+ PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^MIRROR=.*|MIRROR=\"" + PrefStore.MIRROR
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^USER_NAME=.*|USER_NAME=\"" + PrefStore.USER_NAME
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^SERVER_DNS=.*|SERVER_DNS=\""
				+ PrefStore.SERVER_DNS + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^LOCALE=.*|LOCALE=\"" + PrefStore.LOCALE
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^INSTALL_GUI=.*|INSTALL_GUI=\""
				+ PrefStore.INSTALL_GUI + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^DESKTOP_ENV=.*|DESKTOP_ENV=\""
				+ PrefStore.DESKTOP_ENV + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^CUSTOM_STARTUP=.*|CUSTOM_STARTUP=\""
				+ PrefStore.CUSTOM_STARTUP + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^CUSTOM_MOUNT=.*|CUSTOM_MOUNT=\""
				+ PrefStore.CUSTOM_MOUNT + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^SSH_START=.*|SSH_START=\"" + PrefStore.SSH_START
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^SSH_PORT=.*|SSH_PORT=\"" + PrefStore.SSH_PORT
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_START=.*|VNC_START=\"" + PrefStore.VNC_START
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_DISPLAY=.*|VNC_DISPLAY=\""
				+ PrefStore.VNC_DISPLAY + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_DEPTH=.*|VNC_DEPTH=\"" + PrefStore.VNC_DEPTH
				+ "\"|g' " + PrefStore.HOME_DIR + "/etc/deploy.conf");
		params.add("sed -i 's|^VNC_GEOMETRY=.*|VNC_GEOMETRY=\""
				+ PrefStore.VNC_GEOMETRY + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^XSERVER_START=.*|XSERVER_START=\""
				+ PrefStore.XSERVER_START + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^XSERVER_DISPLAY=.*|XSERVER_DISPLAY=\""
				+ PrefStore.XSERVER_DISPLAY + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("sed -i 's|^XSERVER_HOST=.*|XSERVER_HOST=\""
				+ PrefStore.XSERVER_HOST + "\"|g' " + PrefStore.HOME_DIR
				+ "/etc/deploy.conf");
		params.add("[ $? -eq 0 ] && exit 0 || exit 1");

		ExecCmd ex = new ExecCmd(params);
		ex.run();
		if (!ex.status) {
			sendLogs("[RESULT_LN] fail");
			return;
		}
		sendLogs("[RESULT_LN] done");
	}

	public void updateEnv() {
		sendLogs("[PRINT] Updating environment ... ");

		if (PrefStore.HOME_DIR.length() == 0) {
			sendLogs("[RESULT_LN] fail");
			return;
		}

		List<String> params = new ArrayList<String>();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add("mkdir " + PrefStore.HOME_DIR);
		params.add("rm -R " + PrefStore.HOME_DIR + "/bin");
		params.add("rm -R " + PrefStore.HOME_DIR + "/etc");
		params.add("rm -R " + PrefStore.HOME_DIR + "/deploy");
		params.add("chmod 777 " + PrefStore.HOME_DIR);
		params.add("exit");
		ExecCmd ex = new ExecCmd(params);
		ex.run();
		if (!ex.status) {
			sendLogs("[RESULT_LN] fail");
			return;
		}

		boolean copyResult = copyFileOrDir(PrefStore.HOME_DIR,
				PrefStore.ROOT_ASSETS);
		if (!copyResult) {
			sendLogs("[RESULT_LN] fail");
			return;
		}

		params.clear();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add("chmod 755 " + PrefStore.HOME_DIR);
		params.add("chmod 755 " + PrefStore.HOME_DIR + "/bin");
		params.add("chmod 755 " + PrefStore.HOME_DIR + "/bin/busybox");
		params.add(PrefStore.HOME_DIR + "/bin/busybox --install -s "
				+ PrefStore.HOME_DIR + "/bin");
		params.add("PATH=" + PrefStore.HOME_DIR + "/bin:$PATH; export PATH");
		params.add("chmod -R 755 " + PrefStore.HOME_DIR + "/bin");
		params.add("chmod -R a+rX " + PrefStore.HOME_DIR + "/etc "
				+ PrefStore.HOME_DIR + "/deploy");
		params.add("chmod 755 " + PrefStore.HOME_DIR
				+ "/deploy/debootstrap/pkgdetails");
		params.add("chown -R root:root " + PrefStore.HOME_DIR + "/bin "
				+ PrefStore.HOME_DIR + "/etc " + PrefStore.HOME_DIR + "/deploy");
		if (PrefStore.SYMLINK) {
			params.add("rm -f /system/bin/linuxdeploy");
			params.add("ln -s "
					+ PrefStore.HOME_DIR
					+ "/bin/linuxdeploy /system/bin/linuxdeploy || "
					+ "{ mount -o rw,remount /system; rm -f /system/bin/linuxdeploy; ln -s "
					+ PrefStore.HOME_DIR
					+ "/bin/linuxdeploy /system/bin/linuxdeploy; mount -o ro,remount /system; }");
		}
		params.add("echo '" + PrefStore.VERSION_CODE + "' > "
				+ PrefStore.HOME_DIR + "/etc/version");
		params.add("exit");
		ex = new ExecCmd(params);
		ex.run();
		if (!ex.status) {
			sendLogs("[RESULT_LN] fail");
			return;
		}
		sendLogs("[RESULT_LN] done");
	}

	public void deployCmd(String cmd) {
		boolean update = true;
		File f = new File(PrefStore.HOME_DIR + "/etc/version");
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			try {
				String line = br.readLine();
				if (PrefStore.VERSION_CODE.equals(line))
					update = false;
			} finally {
				br.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (update) {
			// new ShellEnv(c).updateEnv();
			sendLogs("[PRINT_LN] Need to update the operating environment!");
			sendLogs("[PRINT_LN] Try Menu -> Settings -> Update ENV");
			return;
		}

		// new ShellEnv(c).updateConfig();
		List<String> params = new ArrayList<String>();
		params.add("su");
		if (PrefStore.TRACE_MODE.equals("y"))
			params.add("set -x");
		params.add("PATH=" + PrefStore.HOME_DIR + "/bin:$PATH; export PATH");
		params.add("echo '[PRINT_LN] >>> begin: " + cmd + "'");
		params.add("cd " + PrefStore.HOME_DIR);
		params.add("export APK_SHELL=1");
		params.add("linuxdeploy " + cmd);
		params.add("echo '[PRINT_LN] <<< end: " + cmd + "'");
		params.add("exit");
		new ExecCmd(params).run();
	}

}
