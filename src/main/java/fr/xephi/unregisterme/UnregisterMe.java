package fr.xephi.unregisterme;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class UnregisterMe extends JavaPlugin {

    private Logger logger;
    private FileHandler fileHandler;
    private FileConfiguration configuration;
    private File protectedUsersFile;
    private FileConfiguration protectedUsers;
    private HikariDataSource dataSource;
    private String tableName;
    private String nameColumn;
    private String passwordColumn;

    @Override
    public void onEnable() {
        logger = getLogger();

        // Load config
        logger.info("Loading config...");
        saveDefaultConfig();
        configuration = getConfig();

        // Setup log file
        fileHandler = null;
        if (configuration.getBoolean("logFile")) {
            File logDirectory = new File(getDataFolder(), "logs");
            logDirectory.mkdirs();
            File logFile = new File(logDirectory, "unregister" + System.currentTimeMillis() / 1000L + ".log");
            try {
                fileHandler = new FileHandler(logFile.getAbsolutePath());
                fileHandler.setFormatter(new SimpleFormatter());
                fileHandler.setLevel(Level.INFO);
                logger.addHandler(this.fileHandler);
            } catch (IOException e) {
                logger.severe("Unable to create the log file!");
                e.printStackTrace();
            }
        }

        // Load protected users
        logger.fine("Loading protected users...");
        protectedUsersFile = new File(getDataFolder(), "protected.yml");
        protectedUsers = YamlConfiguration.loadConfiguration(protectedUsersFile);

        // Init database
        logger.info("Opening DBCP...");
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(configuration.getString("database.jdbcUrl"));
        hikariConfig.setUsername(configuration.getString("database.username"));
        hikariConfig.setPassword(configuration.getString("database.password"));
        dataSource = new HikariDataSource(hikariConfig);
        tableName = configuration.getString("database.tableName");
        nameColumn = configuration.getString("database.nameColumn");
        passwordColumn = configuration.getString("database.passwordColumn");
    }

    @Override
    public void onDisable() {
        logger.info("Closing DBCP...");
        dataSource.close();
        if (fileHandler != null) {
            fileHandler.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getLabel()) {
            case "protectuser": {
                if (args.length != 1) {
                    return false;
                }
                String argument = args[0].toLowerCase();
                sender.sendMessage("Adding '" + argument + "' to the blacklist file...");

                List<String> protectedNames = protectedUsers.getStringList("protected");
                if (!protectedNames.contains(argument)) {
                    protectedNames.add(argument);
                    protectedUsers.set("protected", protectedNames);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                protectedUsers.save(protectedUsersFile);
                                logger.info("Added '" + argument + "' to the blacklist file!");
                                sender.sendMessage("Success!");
                            } catch (IOException e) {
                                sender.sendMessage("Failed to save the blacklist file!");
                                logger.severe("Unable to save blacklist file!");
                                e.printStackTrace();
                            }
                        }
                    }.runTaskAsynchronously(this);
                    return true;
                }
                sender.sendMessage("Already protected!");
                return true;
            }
            case "unprotectuser": {
                if (args.length != 1) {
                    return false;
                }
                String argument = args[0].toLowerCase();
                sender.sendMessage("Removing '" + argument + "' from the blacklist file...");

                List<String> protectedNames = protectedUsers.getStringList("protected");
                if (protectedNames.contains(argument)) {
                    protectedNames.remove(argument);
                    protectedUsers.set("protected", protectedNames);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                protectedUsers.save(protectedUsersFile);
                                logger.info("Removed '" + argument + "' from the blacklist file!");
                                sender.sendMessage("Success!");
                            } catch (IOException e) {
                                sender.sendMessage("Failed to save the blacklist file!");
                                logger.severe("Unable to save blacklist file!");
                                e.printStackTrace();
                            }
                        }
                    }.runTaskAsynchronously(this);
                    return true;
                }
                sender.sendMessage("Not protected!");
                return true;
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("onlyPlayers"));
            return true;
        }
        Player player = (Player) sender;
        String playerName = player.getName();
        String lowercaseName = playerName.toLowerCase();

        if (player.hasPermission("unregisterme.protect") || protectedUsers.getStringList("protected").contains(lowercaseName)) {
            sender.sendMessage(getMessage("protected"));
            logger.warning("User " + playerName + "(IP:" + player.getAddress().getAddress() + ") tried to unregister/changepassword, but the account is protected!");
            return true;
        }

        switch (command.getLabel()) {
            case "unregister": {
                if (!configuration.getBoolean("unregister.enabled")) {
                    return true;
                }

                logger.info("User " + playerName + "(IP:" + player.getAddress().getAddress() + ") is performing an unregister...");
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        String sql = "DELETE FROM " + tableName + " WHERE " + nameColumn + "=?;";
                        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                            statement.setString(1, playerName.toLowerCase());
                            statement.executeUpdate();
                            logger.info("User " + playerName + " have been unregistered!");
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    String message = getMessage("unregisterSuccess");
                                    if (getConfig().getBoolean("unregister.kick")) {
                                        player.kickPlayer(message);
                                    } else {
                                        sender.sendMessage(message);
                                    }
                                    getConfig().getStringList("unregister.postCommands").forEach(command ->
                                            getServer().dispatchCommand(getServer().getConsoleSender(), command.replace("%player%", playerName)));
                                }
                            }.runTask(UnregisterMe.this);
                            return;
                        } catch (SQLException e) {
                            logger.warning("An error occurred!");
                            e.printStackTrace();
                        }
                        sender.sendMessage(getMessage("error"));
                    }
                }.runTaskAsynchronously(this);
                return true;
            }
            case "changepassword": {
                if (!configuration.getBoolean("changepassword.enabled")) {
                    return true;
                }

                if (args.length < 1) {
                    return false;
                }
                String newPassword = args[0];

                logger.info("User " + playerName + "(IP:" + player.getAddress().getAddress() + ") is changing password...");
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        String newHash = Sha256.computeHash(newPassword);
                        String sql = "UPDATE " + tableName + " SET " + passwordColumn + "=? " + " WHERE " + nameColumn + "=?;";
                        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                            statement.setString(1, newHash);
                            statement.setString(2, playerName.toLowerCase());
                            statement.executeUpdate();
                            logger.info("User " + playerName + " has changed password correctly!");
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    String message = getMessage("changepasswordSuccess");
                                    if (getConfig().getBoolean("changepassword.kick")) {
                                        player.kickPlayer(message);
                                    } else {
                                        sender.sendMessage(message);
                                    }
                                    getConfig().getStringList("changepassword.postCommands").forEach(command ->
                                            getServer().dispatchCommand(getServer().getConsoleSender(), command.replace("%player%", playerName)));
                                }
                            }.runTask(UnregisterMe.this);
                            return;
                        } catch (SQLException e) {
                            logger.warning("An error occurred!");
                            e.printStackTrace();
                        }
                        sender.sendMessage(getMessage("error"));
                    }
                }.runTaskAsynchronously(this);
                return true;
            }
        }
        return true;
    }

    private String getMessage(String id) {
        return ChatColor.translateAlternateColorCodes('&', configuration.getString("messages." + id));
    }
}
