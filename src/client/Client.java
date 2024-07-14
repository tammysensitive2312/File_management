package client;

import utils.User;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;
import java.util.Scanner;


public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Welcome to the File Management System");
            while (true) {
                System.out.println("1. Login");
                System.out.println("2. Register");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine();
                if ("1".equals(choice)) {
                    out.writeObject("login");
                    authenticate(scanner, in, out);
                    handleRegularUser(scanner, in, out);
                    break;
                } else if ("2".equals(choice)) {
                    out.writeObject("register");
                    register(scanner, in, out);
                } else {
                    System.out.println("Invalid choice. Try again.");
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void authenticate(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        while (true) {
            System.out.println("Enter username:");
            String username = scanner.nextLine();
            System.out.println("Enter password:");
            String password = scanner.nextLine();
            User user = new User(username, password);
            out.writeObject(user);

            String response = (String) in.readObject();
            if ("success".equals(response)) {
                System.out.println("Authentication successful");
                return;
            } else {
                System.out.println("Authentication failed. Try again.");
            }
        }
    }

    private static void register(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Enter username for registration:");
        String username = scanner.nextLine();
        System.out.println("Enter password for registration:");
        String password = scanner.nextLine();
        User newUser = new User(username, password);
        out.writeObject(newUser);

        String response = (String) in.readObject();
        if ("registered".equals(response)) {
            System.out.println("Registration successful. You can now login.");
        } else if ("exists".equals(response)) {
            System.out.println("Username already exists. Please choose another username.");
        }
    }


    private static void handleRegularUser(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        try {
            String currentDir = (String) in.readObject();
            while (true) {
                System.out.println("Current directory: " + currentDir);
                System.out.println("Menu:");
                System.out.println("1. Upload File");
                System.out.println("2. Download File");
                System.out.println("3. Manage folder");
                System.out.println("4. Manage File");
                System.out.println("5. Move to");
                System.out.println("6. Back");
                System.out.println("7. Exit");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine();
                switch (choice) {
                    case "1":
                        out.writeObject("upload");
                        uploadFile(scanner, in, out);
                        break;
                    case "2":
                        out.writeObject("download");
                        downloadFile(scanner, in, out, currentDir);
                        break;
                    case "3":
                        out.writeObject("manage folder");
                        manageFolder(scanner, in, out);
                        break;
                    case "4":
                        out.writeObject("manage file");
                        manageFile(scanner, in, out);
                        break;
                    case "5":
                        out.writeObject("move to");
                        moveToDirectory(scanner, in, out);
                        currentDir = (String) in.readObject();
                        break;
                    case "6":
                        out.writeObject("back");
                        moveBackDirectory(in);
                        currentDir = (String) in.readObject();
                        break;
                    case "7":
                        out.writeObject("exit");
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void uploadFile(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Enter the path of the file to upload:");
        String filePath = scanner.nextLine();
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            System.out.println("Invalid file path. Please try again.");
            return;
        }

        byte[] fileData = Files.readAllBytes(file.toPath());
        out.writeObject(file.getName());
        out.writeObject(fileData);
        System.out.println((String) in.readObject());
    }

    private static void downloadFile(Scanner scanner, ObjectInputStream in, ObjectOutputStream out, String currentDir) throws IOException, ClassNotFoundException {
        System.out.println("Enter the name of the file to download:");
        String fileName = scanner.nextLine();
        out.writeObject(fileName);

        byte[] fileData = (byte[]) in.readObject();

        File file = new File(currentDir, fileName);
        Files.write(file.toPath(), fileData);

        String response = (String) in.readObject();
        System.out.println(response);
    }


    private static void manageFolder(Scanner sc, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Folder Management:");
        System.out.println("1. Create Folder");
        System.out.println("2. Rename Folder");
        System.out.println("3. Delete Folder");
        System.out.println("4. View List");
        System.out.println("5. Back");
        System.out.print("Choose an option: ");
        String choice = sc.nextLine();
        switch (choice) {
            case "1":
                out.writeObject("create");
                createDirectory(sc, in, out);
                break;
            case "2":
                out.writeObject("rename");
                renameDirectory(sc, in, out);
                break;
            case "3":
                out.writeObject("delete");
                deleteDirectory(sc, in, out);
                break;
            case "4":
                out.writeObject("view folder");
                listCurrentDirectory(in);
                break;
            case "5":
                out.writeObject("Back");
                break;
            default:
                System.out.println("Invalid choice. Try again.");
                break;
        }
    }

    private static void createDirectory(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Enter the name of the directory to create:");
        String dirName = scanner.nextLine();
        out.writeObject(dirName);
        System.out.println((String) in.readObject());
    }

    private static void renameDirectory(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.print("Enter the current name of the directory: ");
        String oldDirName = scanner.nextLine();
        System.out.print("Enter the new name of the directory: ");
        String newDirName = scanner.nextLine();
        out.writeObject(oldDirName);
        out.writeObject(newDirName);
        System.out.println((String) in.readObject());
    }

    private static void deleteDirectory(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Enter the name of the directory to delete: ");
        String dirName = scanner.nextLine();
        out.writeObject(dirName);

        String serverResponse = (String) in.readObject();
        System.out.println(serverResponse);

        if ("Directory containing content. Continue? Y/n".equals(serverResponse)) {
            String response = scanner.nextLine();
            out.writeObject(response);
            System.out.println((String) in.readObject());
        } else {
            System.out.println(serverResponse);
        }
    }

    private static void moveToDirectory(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Enter the name of the directory to move to:");
        String dirName = scanner.nextLine();
        out.writeObject(dirName);

        String newCurrentDir = (String) in.readObject();
        System.out.println("Current directory: " + newCurrentDir);
    }


    private static void listCurrentDirectory(ObjectInputStream in) throws IOException, ClassNotFoundException {
        List<String> files = (List<String>) in.readObject();
        System.out.println("Current directory contents:");
        for (String file : files) {
            System.out.println(file);
        }
    }

    private static void moveBackDirectory(ObjectInputStream in) throws IOException, ClassNotFoundException {
        String newCurrentDir = (String) in.readObject();
        System.out.println("Current directory: " + newCurrentDir);
    }

    private static void manageFile(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("File Management:");
        System.out.println("1. Create File");
        System.out.println("2. Rename File");
        System.out.println("3. Delete File");
        System.out.println("4. View File Content");
        System.out.println("5. Copy File");
        System.out.println("6. Move File");
        System.out.println("7. Back");
        System.out.print("Choose an option: ");
        String choice = scanner.nextLine();
        switch (choice) {
            case "1":
                out.writeObject("create");
                createFile(scanner, out);
                break;
            case "2":
                out.writeObject("rename");
                renameFile(scanner, out);
                break;
            case "3":
                out.writeObject("delete");
                deleteFile(scanner, out);
                break;
            case "4":
                out.writeObject("view file");
                viewFile(scanner, in, out);
                break;
            case "5":
                out.writeObject("copy");
                copyFile(scanner, out);
                break;
            case "6":
                out.writeObject("move");
                moveFile(scanner, out);
                break;
            case "7":
                out.writeObject("exit");
                return;
            default:
                System.out.println("Invalid choice. Try again.");
                return;
        }
        System.out.println((String) in.readObject());
    }

    private static void createFile(Scanner scanner, ObjectOutputStream out) throws IOException {
        System.out.println("Enter the absolute path of the new file:");
        String filePath = scanner.nextLine();
        out.writeObject(filePath);
    }


    private static void renameFile(Scanner scanner, ObjectOutputStream out) throws IOException {
        System.out.println("Enter the absolute path of the file to rename:");
        String sourcePath = scanner.nextLine();
        out.writeObject(sourcePath);

        System.out.println("Enter the new name of the file:");
        String newName = scanner.nextLine();
        out.writeObject(newName);
    }

    private static void deleteFile(Scanner scanner, ObjectOutputStream out) throws IOException {
        System.out.println("Enter the absolute path of the file to delete:");
        String filePath = scanner.nextLine();
        out.writeObject(filePath);
    }

    private static void viewFile(Scanner scanner, ObjectInputStream in, ObjectOutputStream out) throws IOException, ClassNotFoundException {
        System.out.println("Enter the absolute path of the file to view:");
        String filePath = scanner.nextLine();
        out.writeObject(filePath);

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) in.readObject();
        for (String line : lines) {
            System.out.println(line);
        }
    }

    private static void copyFile(Scanner scanner, ObjectOutputStream out) throws IOException {
        System.out.println("Enter the absolute path of the source file:");
        String sourcePath = scanner.nextLine();
        out.writeObject(sourcePath);

        System.out.println("Enter the absolute path of the destination directory:");
        String destPath = scanner.nextLine();
        out.writeObject(destPath);
    }

    private static void moveFile(Scanner scanner, ObjectOutputStream out) throws IOException {
        System.out.println("Enter the absolute path of the source file:");
        String sourcePath = scanner.nextLine();
        out.writeObject(sourcePath);

        System.out.println("Enter the absolute path of the destination directory:");
        String destPath = scanner.nextLine();
        out.writeObject(destPath);
    }

}
