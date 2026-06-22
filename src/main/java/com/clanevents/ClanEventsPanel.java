package com.clanevents;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class ClanEventsPanel extends PluginPanel
{
	private static final String CARD_LIST = "list";
	private static final String CARD_CREATE = "create";

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cardContainer = new JPanel(cardLayout);

	private final JPanel eventListPanel = new JPanel();
	private final JLabel statusLabel = new JLabel();
	private EventCreatePanel createPanel;

	private final ClanEventsPlugin plugin;

	ClanEventsPanel(ClanEventsPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setLayout(new BorderLayout());

		cardContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// --- List view ---
		JPanel listView = new JPanel(new BorderLayout(0, 4));
		listView.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		listView.add(buildListHeader(), BorderLayout.NORTH);

		eventListPanel.setLayout(new BoxLayout(eventListPanel, BoxLayout.Y_AXIS));
		eventListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scroll = new JScrollPane(eventListPanel);
		scroll.setBorder(null);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		listView.add(scroll, BorderLayout.CENTER);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
		listView.add(statusLabel, BorderLayout.SOUTH);

		cardContainer.add(listView, CARD_LIST);

		add(cardContainer, BorderLayout.CENTER);

		showLoading();
	}

	private JPanel buildListHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(8, 8, 8, 8));

		JLabel title = new JLabel("Clan Events");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());

		JButton addBtn = new JButton("+");
		addBtn.setToolTipText("Create new event");
		addBtn.setFont(new Font(addBtn.getFont().getName(), Font.BOLD, 16));
		addBtn.setForeground(new Color(0x1ABC9C));
		addBtn.setBorderPainted(false);
		addBtn.setContentAreaFilled(false);
		addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addBtn.addActionListener(e -> showCreateForm());

		header.add(title, BorderLayout.WEST);
		header.add(addBtn, BorderLayout.EAST);
		return header;
	}

	public void setEvents(List<ClanEvent> events, String currentUsername)
	{
		SwingUtilities.invokeLater(() ->
		{
			eventListPanel.removeAll();
			statusLabel.setText("");

			if (events.isEmpty())
			{
				JLabel empty = new JLabel("No upcoming events. Press + to create one.");
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				empty.setFont(FontManager.getRunescapeSmallFont());
				empty.setBorder(new EmptyBorder(12, 8, 0, 8));
				empty.setAlignmentX(Component.LEFT_ALIGNMENT);
				eventListPanel.add(empty);
			}
			else
			{
				for (ClanEvent event : events)
				{
					boolean isHost = event.host.equalsIgnoreCase(currentUsername);
					boolean canDelete = isHost || plugin.currentUserCanAdminDelete();
					EventCardPanel card = new EventCardPanel(event, currentUsername, canDelete,
						plugin::rsvpEvent, plugin::deleteEvent);
					card.setAlignmentX(Component.LEFT_ALIGNMENT);
					card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
					eventListPanel.add(card);
				}
			}

			eventListPanel.revalidate();
			eventListPanel.repaint();
		});
	}

	public void showLoading()
	{
		SwingUtilities.invokeLater(() ->
		{
			eventListPanel.removeAll();
			JLabel loading = new JLabel("Loading events...");
			loading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			loading.setFont(FontManager.getRunescapeSmallFont());
			loading.setBorder(new EmptyBorder(12, 8, 0, 8));
			eventListPanel.add(loading);
			eventListPanel.revalidate();
			eventListPanel.repaint();
		});
	}

	public void showConfigRequired()
	{
		SwingUtilities.invokeLater(() ->
		{
			eventListPanel.removeAll();
			JLabel msg = new JLabel("<html><center>Configure your JSONBin<br>credentials in Plugin Settings.</center></html>");
			msg.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			msg.setFont(FontManager.getRunescapeSmallFont());
			msg.setBorder(new EmptyBorder(12, 8, 0, 8));
			msg.setHorizontalAlignment(SwingConstants.CENTER);
			eventListPanel.add(msg);
			eventListPanel.revalidate();
			eventListPanel.repaint();
		});
	}

	public void showError(String message)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(message));
	}

	private void showCreateForm()
	{
		String username = plugin.getCurrentUsername();

		createPanel = new EventCreatePanel(
			username,
			event ->
			{
				// Disable the form while saving
				createPanel.setStatus("Saving...", false);
				plugin.createEvent(event,
					() -> SwingUtilities.invokeLater(this::showList),
					() -> createPanel.setStatus("Failed to save — check your connection.", true));
			},
			this::showList
		);

		// Replace previous create panel if any
		cardContainer.remove(cardContainer.getComponentCount() > 1
			? cardContainer.getComponent(1)
			: createPanel);
		cardContainer.add(createPanel, CARD_CREATE);
		cardLayout.show(cardContainer, CARD_CREATE);
	}

	private void showList()
	{
		cardLayout.show(cardContainer, CARD_LIST);
	}
}
