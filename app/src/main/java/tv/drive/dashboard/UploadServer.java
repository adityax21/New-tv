package tv.drive.dashboard;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class UploadServer {
    private final MainActivity activity;
    private volatile boolean running = true;
    private ServerSocket server;
    private final File uploadDir;

    public UploadServer(MainActivity activity) throws IOException {
        this.activity = activity;
        uploadDir = new File(activity.getFilesDir(), "received-audio");
        if (!uploadDir.exists()) uploadDir.mkdirs();
    }

    public void start() {
        Thread t = new Thread(() -> {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(8787));
                while (running) {
                    Socket s = server.accept();
                    new Thread(() -> handle(s)).start();
                }
            } catch (IOException ignored) {}
        }, "drive-upload-server");
        t.setDaemon(true);
        t.start();
    }

    private void writeResponse(OutputStream out, int code, String status, String type, byte[] body) throws IOException {
        String h = "HTTP/1.1 " + code + " " + status + "\r\n" +
            "Content-Type: " + type + "\r\n" +
            "Content-Length: " + body.length + "\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type, X-Filename\r\n" +
            "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private void handle(Socket socket) {
        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {
            s.setSoTimeout(15000);
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                byte[] hb = headerBuf.toByteArray();
                int n = hb.length;
                if (n >= 4 && hb[n-4] == '\r' && hb[n-3] == '\n' &&
                    hb[n-2] == '\r' && hb[n-1] == '\n') break;
                if (headerBuf.size() > 16384) return;
            }
            String headers = headerBuf.toString("UTF-8");
            String[] lines = headers.split("\r\n");
            if (lines.length == 0) return;
            String[] first = lines[0].split(" ");
            String method = first.length > 0 ? first[0] : "GET";
            String path = first.length > 1 ? first[1] : "/";
            int length = 0;
            String filename = "ipad-audio.bin";
            for (String line : lines) {
                String low = line.toLowerCase();
                if (low.startsWith("content-length:")) length = Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                if (low.startsWith("x-filename:")) filename = line.substring(line.indexOf(':')+1).trim();
            }
            if ("OPTIONS".equals(method)) {
                writeResponse(out, 204, "No Content", "text/plain", new byte[0]); return;
            }
            if ("GET".equals(method) && "/".equals(path)) {
                String page = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>Send music to DRIVE TV</title><style>body{font:17px Arial;background:#080b10;color:#f5f7fb;padding:28px;max-width:620px;margin:auto}main{background:#141b26;border-radius:20px;padding:24px}button{background:#b7f36b;border:0;border-radius:12px;padding:14px;font-weight:bold}input{margin:18px 0;max-width:100%}.muted{color:#9da8b8}</style>" +
                    "<main><h1>Send music to DRIVE TV</h1><p class='muted'>Your iPad and TV must be on the same Wi-Fi network. Choose an audio file from Files, then send it to the TV.</p>" +
                    "<input id='f' type='file' accept='audio/*,.mp3,.m4a,.aac,.wav,.ogg,.flac'><br><button onclick='send()'>Send to TV</button><p id='s'></p></main>" +
                    "<script>async function send(){let f=document.getElementById('f').files[0],s=document.getElementById('s');if(!f){s.textContent='Choose an audio file first.';return}s.textContent='Sending '+f.name+'…';try{let r=await fetch('/upload',{method:'POST',headers:{'Content-Type':'application/octet-stream','X-Filename':encodeURIComponent(f.name)},body:f});s.textContent=r.ok?'Sent! Return to the TV and press Play.':'Upload failed: '+await r.text()}catch(e){s.textContent='Could not connect. Check that both devices are on the same Wi-Fi.'}}</script>";
                writeResponse(out, 200, "OK", "text/html; charset=utf-8", page.getBytes(StandardCharsets.UTF_8)); return;
            }
            if ("POST".equals(method) && "/upload".equals(path)) {
                if (length <= 0 || length > 80 * 1024 * 1024) {
                    writeResponse(out, 413, "Payload Too Large", "text/plain", "Choose a file under 80 MB.".getBytes(StandardCharsets.UTF_8)); return;
                }
                try { filename = URLDecoder.decode(filename, "UTF-8"); } catch (Exception ignored) {}
                filename = filename.replaceAll("[^A-Za-z0-9._ -]", "_");
                if (filename.trim().isEmpty()) filename = "ipad-audio.bin";
                File target = new File(uploadDir, filename);
                FileOutputStream fos = new FileOutputStream(target);
                byte[] buf = new byte[8192]; int remaining = length;
                while (remaining > 0) {
                    int n = in.read(buf, 0, Math.min(buf.length, remaining));
                    if (n < 0) throw new EOFException();
                    fos.write(buf, 0, n); remaining -= n;
                }
                fos.close();
                activity.setCurrentAudio(target);
                writeResponse(out, 200, "OK", "text/plain; charset=utf-8", "Audio received.".getBytes(StandardCharsets.UTF_8)); return;
            }
            writeResponse(out, 404, "Not Found", "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public void stopServer() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
    }
}
