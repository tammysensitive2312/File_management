package server;

import utils.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Server {
    private static final int PORT = 12345;
    private static List<User> userList = new ArrayList<>();
    private static final String USERS_FILE = "res/data/users.txt";
    private static final String PATHS_FILE = "res/data/paths.txt";
    private static final String UPLOAD_FOLDER = "res/server_files/";
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());


    public static void main(String[] args) {
        loadUsers();
        new Server().start();
    }

    private void start() {
        // Tạo thư mục upload nếu chưa tồn tại
        new File(UPLOAD_FOLDER).mkdirs();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running...");
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Server error: ", e);
        }
    }

    private static void loadUsers() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(USERS_FILE))) {
            userList = (List<User>) ois.readObject();
        } catch (EOFException e) {
            saveUsers();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Error loading users: ", e);
        }

    }

    private static void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(userList);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving users: ", e);
        }
    }

    private static void savePaths(List<String> paths) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PATHS_FILE))) {
            for (String path : paths) {
                writer.write(path);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving paths: ", e);
        }
    }

    public static class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String currentDir;
        private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                setupStreams();
                LOGGER.info("Client connected: " + socket.getInetAddress());

                while (true) {
                    try {
                        String command = (String) in.readObject();
                        LOGGER.info("Received command: " + command);

                        if ("login".equals(command)) {
                            User user = authenticate();
                            currentDir = Paths.get(UPLOAD_FOLDER, user.getUsername()).toString();
                            Files.createDirectories(Paths.get(currentDir));
                            out.writeObject(currentDir);
                            LOGGER.info("User authenticated and directory set: " + currentDir);
                            handleRegularUser();
                        } else if ("register".equals(command)) {
                            registerUser();
                            LOGGER.info("User registered.");
                        }
                    } catch (EOFException e) {
                        LOGGER.warning("Client disconnected unexpectedly.");
                        break;
                    } catch (ClassNotFoundException | IOException e) {
                        LOGGER.log(Level.SEVERE, "Error during communication with client.", e);
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "IO Error during setup.", e);
            } finally {
                closeConnections();
                LOGGER.info("Connection closed.");
            }
        }

        private void handleRegularUser() throws IOException, ClassNotFoundException {
            while (true) {
                String command = (String) in.readObject();
                switch (command) {
                    case "upload":
                        uploadFile();
                        break;
                    case "download":
                        downloadFile();
                        break;
                    case "manage folder":
                        manageFolder();
                        break;
                    case "manage file":
                        manageFile();
                        break;
                    case "move to":
                        moveToDirectory();
                        break;
                    case "back":
                        goBackToParentDirectory();
                        break;
                    case "exit":
                        return;
                    default:
                        out.writeObject("Invalid command");
                        break;
                }
            }
        }


        private void setupStreams() throws IOException {
            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
        }

        private void closeConnections() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private User authenticate() throws IOException, ClassNotFoundException {
            while (true) {
                User user = (User) in.readObject();
                for (User u : userList) {
                    if (u.getUsername().equals(user.getUsername()) && u.getPassword().equals(user.getPassword())) {
                        out.writeObject("success");
                        return user;
                    }
                }
                out.writeObject("fail");
            }
        }

        private void registerUser() throws IOException, ClassNotFoundException {
            User newUser = (User) in.readObject();
            boolean userExists = userList.stream().anyMatch(u -> u.getUsername().equals(newUser.getUsername()));
            if (userExists) {
                out.writeObject("exists");
            } else {
                userList.add(newUser);
                saveUsers();
                out.writeObject("registered");
            }
        }

        private void uploadFile() throws IOException, ClassNotFoundException {
            String fileName = (String) in.readObject();
            byte[] fileData = (byte[]) in.readObject();
            Files.write(Paths.get(currentDir + "/" + fileName), fileData);
            updatePaths();
            out.writeObject("File uploaded successfully.");
        }

        private void downloadFile() throws IOException, ClassNotFoundException {
            String fileName = (String) in.readObject();
            Path filePath = Paths.get(UPLOAD_FOLDER, fileName);

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                byte[] fileData = Files.readAllBytes(filePath);
                out.writeObject(fileData);
                out.writeObject("File downloaded successfully.");
            } else {
                out.writeObject("Invalid file.");
            }
        }


        private void createDirectory() throws IOException, ClassNotFoundException {
            String dirName = (String) in.readObject();
            Files.createDirectory(Paths.get(currentDir + "/" + dirName));
            updatePaths();
            out.writeObject("Directory created successfully.");
        }

        private void renameDirectory() throws IOException, ClassNotFoundException {
            String oldDirName = (String) in.readObject();
            String newDirName = (String) in.readObject();
            Path sourcePath = Paths.get(currentDir + "/" + oldDirName);
            Path targetPath = Paths.get(currentDir + "/" + newDirName);
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            updatePaths();
            out.writeObject("Directory renamed successfully.");
        }

        private void deleteDirectory() throws IOException, ClassNotFoundException {
            String dirName = (String) in.readObject();
            Path dirPath = Paths.get(currentDir + "/" + dirName);

            if (!Files.exists(dirPath)) {
                out.writeObject("Directory does not exist.");
                return;
            }

            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath)) {
                if (dirStream.iterator().hasNext()) {
                    out.writeObject("Directory containing content. Continue? Y/n");
                    String response = (String) in.readObject();
                    if ("Y".equalsIgnoreCase(response)) {
                        deleteDirectoryRecursively(dirPath);
                        out.writeObject("Directory deleted successfully.");
                    } else {
                        out.writeObject("Deletion cancelled.");
                    }
                } else {
                    Files.delete(dirPath);
                    out.writeObject("Directory deleted successfully.");
                }
            } catch (IOException e) {
                out.writeObject("Error deleting directory: " + e.getMessage());
            }
            updatePaths();
        }

        private void deleteDirectoryRecursively(Path path) throws IOException {
            if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                    for (Path entry : entries) {
                        deleteDirectoryRecursively(entry);
                    }
                }
            }
            Files.delete(path);
        }

        private void moveToDirectory() throws IOException, ClassNotFoundException {
            String targetDir = (String) in.readObject();
            Path newPath = Paths.get(currentDir, targetDir).normalize();
            if (Files.isDirectory(newPath) && newPath.startsWith(UPLOAD_FOLDER)) {
                currentDir = newPath.toString();
                out.writeObject("Moved to: " + currentDir);
                out.writeObject(currentDir);
            } else {
                out.writeObject("Invalid directory");
                out.writeObject(currentDir);
            }
        }


        private void listCurrentDirectory() throws IOException {
            try (Stream<Path> paths = Files.list(Path.of(currentDir))) {
                List<String> files = paths.map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                out.writeObject(files);
            }
        }

        private void goBackToParentDirectory() throws IOException {
            Path currentPath = Paths.get(currentDir);
            Path parentPath = currentPath.getParent();
            Path serverFilesPath = Paths.get("res/server_files");

            if (parentPath != null) {
                if (parentPath.equals(serverFilesPath)) {
                    out.writeObject("Already at root directory or invalid move");
                } else {
                    currentDir = parentPath.toString();
                    out.writeObject("Moved back to: " + currentDir);
                }
            } else {
                out.writeObject("Already at root directory or invalid move");
            }
            out.writeObject(currentDir);
        }



        private void manageFolder() throws IOException, ClassNotFoundException {
            String action = (String) in.readObject();
            switch (action) {
                case "create":
                    createDirectory();
                    break;
                case "rename":
                    renameDirectory();
                    break;
                case "delete":
                    deleteDirectory();
                    break;
                case "view folder":
                    listCurrentDirectory();
                    break;
                case "Back":
//                    goBackToParentDirectory();
//                    break;
                    return;
                default:
                    out.writeObject("Invalid folder management command");
                    break;
            }
        }

        private void manageFile() throws IOException, ClassNotFoundException {
            String action = (String) in.readObject();
            switch (action) {
                case "create":
                    createFile();
                    break;
                case "rename":
                    renameFile();
                    break;
                case "delete":
                    deleteFile();
                    break;
                case "view file":
                    viewFile();
                    break;
                case "copy":
                    copyFile();
                    break;
                case "move":
                    moveFile();
                    break;
                case "exit":
                    return;
                default:
                    out.writeObject("Invalid file management command.");
                    break;
            }
        }

        private void createFile() throws IOException, ClassNotFoundException {
            String filePathStr = (String) in.readObject();
            Path filePath = Paths.get(filePathStr);

            if (Files.exists(filePath)) {
                out.writeObject("File already exists.");
                return;
            }

            Files.createFile(Path.of(currentDir + "/" + filePath));
            updatePaths();
            out.writeObject("File created successfully.");
        }

        private void renameFile() throws IOException, ClassNotFoundException {
            String sourcePathStr = (String) in.readObject();
            String newName = (String) in.readObject();
            Path sourcePath = Paths.get(currentDir + "/" + sourcePathStr);
            Path targetPath = sourcePath.resolveSibling(newName);

            if (!Files.exists(sourcePath)) {
                out.writeObject("Source file does not exist.");
                return;
            }

            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            updatePaths();
            out.writeObject("File renamed successfully.");
        }

        private void viewFile() throws IOException, ClassNotFoundException {
            String filePathStr = (String) in.readObject();
            Path filePath = Paths.get(filePathStr);

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                out.writeObject("File does not exist or is a directory.");
                return;
            }

            List<String> lines = Files.readAllLines(filePath);
            out.writeObject(lines);
            out.writeObject("File content retrieved successfully.");
        }

        private void deleteFile() throws IOException, ClassNotFoundException {
            String filePathStr = (String) in.readObject();
            Path filePath = Paths.get(currentDir + "/" + filePathStr);

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                out.writeObject("File does not exist or is a directory.");
                return;
            }

//            Files.delete(Path.of(currentDir + "/" + filePath));
            Files.delete(filePath);
            out.writeObject("File deleted successfully.");
        }


        private void copyFile() throws IOException, ClassNotFoundException {
            String sourcePathStr = (String) in.readObject();
            String destPathStr = (String) in.readObject();
            Path sourcePath = Paths.get(sourcePathStr);
            Path destPath = Paths.get(destPathStr, sourcePath.getFileName().toString());
            if (!Files.exists(sourcePath) || Files.isDirectory(sourcePath)) {
                out.writeObject("Source file does not exist or is a directory.");
                return;
            }
            if (!Files.exists(destPath.getParent()) || !Files.isDirectory(destPath.getParent())) {
                out.writeObject("Destination directory does not exist.");
                return;
            }
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            out.writeObject("File copied successfully.");
        }

        private void moveFile() throws IOException, ClassNotFoundException {
            String sourcePathStr = (String) in.readObject();
            String destPathStr = (String) in.readObject();
            Path sourcePath = Paths.get(sourcePathStr);
            Path destPath = Paths.get(destPathStr, sourcePath.getFileName().toString());
            if (!Files.exists(sourcePath) || Files.isDirectory(sourcePath)) {
                out.writeObject("Source file does not exist or is a directory.");
                return;
            }
            if (!Files.exists(destPath.getParent()) || !Files.isDirectory(destPath.getParent())) {
                out.writeObject("Destination directory does not exist.");
                return;
            }
            Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            out.writeObject("File moved successfully.");
        }


        private void updatePaths() throws IOException {
            List<String> paths = new ArrayList<>();
            Files.walk(Paths.get(currentDir)).forEach(path -> paths.add(path.toString()));
            savePaths(paths);
        }
    }
}