package fr.xephi.unregisterme;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class UnregisterMe extends JavaPlugin {

    private Logger logger;
    private FileConfiguration configuration;
    private HikariDataSource dataSource;
    private String tableName;
    private String nameColumn;

    @Override
    public void onEnable() {
        logger = getLogger();

        // Load config
        logger.info("Loading config...");
        saveDefaultConfig();
        configuration = getConfig();

        // Init database
        logger.info("Opening DBCP...");
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(configuration.getString("database.jdbcUrl"));
        hikariConfig.setUsername(configuration.getString("database.username"));
        hikariConfig.setPassword(configuration.getString("database.password"));
        dataSource = new HikariDataSource(hikariConfig);
        tableName = configuration.getString("database.tableName");
        nameColumn = configuration.getString("database.nameColumn");
    }

    @Override
    public void onDisable() {
        logger.info("Closing DBCP...");
        dataSource.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)) {
            sender.sendMessage(getMessage("onlyPlayers"));
            return true;
        }
        Player player = (Player) sender;
        String playerName = player.getName();
        logger.info("User " + playerName + "(IP:" + player.getAddress().getAddress() + ") is performing an unregister...");
        new BukkitRunnable() {

            @Override
            public void run() {
                String sql = "DELETE FROM " + tableName + " WHERE " + nameColumn + "=?;";
                try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, playerName.toLowerCase());
                    statement.executeUpdate();
                    sender.sendMessage(getMessage("succes"));
                    logger.info("User " + playerName + " have been unregistered!");
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

    private String getMessage(String id) {
        return ChatColor.translateAlternateColorCodes('&', configuration.getString("messages." + id));
    }
}
