package com.clanevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

@Slf4j
public class ClanEventsPanel extends PluginPanel
{
	private static final String CARD_LIST   = "list";
	private static final String CARD_CREATE = "create";
	private static final Color  TEAL        = new Color(0x1ABC9C);
	private static final Color  TEAL_HOVER  = new Color(0x16A085);
	private static final Color  ERROR_COLOR = new Color(0xE74C3C);
	private static final Color  SEPARATOR   = new Color(0x2E2E2E);

	private final CardLayout cardLayout    = new CardLayout();
	private final JPanel     cardContainer = new JPanel(cardLayout);
	private final JPanel     listView;       // field so showCreateForm can remove non-listView components safely
	private final JPanel     eventListPanel = new JPanel();
	private final JLabel     statusLabel    = new JLabel();
	private EventCreatePanel createPanel;

	private final ClanEventsPlugin plugin;

	ClanEventsPanel(ClanEventsPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setLayout(new BorderLayout());

		cardContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// ── List view ─────────────────────────────────────────────────────────
		listView = new JPanel(new BorderLayout());
		listView.setBackground(ColorScheme.DARK_GRAY_COLOR);

		listView.add(buildListHeader(), BorderLayout.NORTH);

		eventListPanel.setLayout(new BoxLayout(eventListPanel, BoxLayout.Y_AXIS));
		eventListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scroll = new JScrollPane(eventListPanel);
		scroll.setBorder(null);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listView.add(scroll, BorderLayout.CENTER);

		// Status bar — errors appear here; invisible when empty
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setBorder(new EmptyBorder(4, 10, 6, 10));
		listView.add(statusLabel, BorderLayout.SOUTH);

		cardContainer.add(listView, CARD_LIST);
		add(cardContainer, BorderLayout.CENTER);

		showLoading();
	}

	private JPanel buildListHeader()
	{
		// Wrapper so we can add the separator below the header without affecting layout
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(10, 10, 10, 8));

		JLabel titleLbl = new JLabel("Clan Event Board");
		titleLbl.setForeground(Color.WHITE);
		titleLbl.setFont(FontManager.getRunescapeBoldFont());

		// "+" button — teal with hover feedback, clearly interactive
		JButton addBtn = new JButton("+");
		addBtn.setToolTipText("Create new event");
		addBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		addBtn.setForeground(TEAL);
		addBtn.setBorderPainted(false);
		addBtn.setContentAreaFilled(false);
		addBtn.setFocusPainted(false);
		addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addBtn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { addBtn.setForeground(TEAL_HOVER); }

			@Override
			public void mouseExited(MouseEvent e)  { addBtn.setForeground(TEAL); }
		});
		addBtn.addActionListener(e -> showCreateForm());

		header.add(titleLbl, BorderLayout.WEST);
		header.add(addBtn, BorderLayout.EAST);

		// Thin separator provides a clean break between header chrome and scrollable content
		JSeparator sep = new JSeparator();
		sep.setForeground(SEPARATOR);
		sep.setBackground(ColorScheme.DARK_GRAY_COLOR);

		wrapper.add(header, BorderLayout.CENTER);
		wrapper.add(sep, BorderLayout.SOUTH);
		return wrapper;
	}

	public void setEvents(List<ClanEvent> events, String currentUsername, boolean canAdminDelete)
	{
		SwingUtilities.invokeLater(() ->
		{
			eventListPanel.removeAll();
			statusLabel.setText(""); // clear any previous error
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			if (events.isEmpty())
			{
				eventListPanel.add(buildEmptyState("No upcoming events.", "Press + to create one."));
			}
			else
			{
				for (ClanEvent event : events)
				{
					try
					{
						boolean isHost = currentUsername != null && event.host != null
							&& event.host.equalsIgnoreCase(currentUsername);
						EventCardPanel card = new EventCardPanel(event, currentUsername,
							isHost || canAdminDelete, plugin::rsvpEvent, plugin::deleteEvent);
						card.setAlignmentX(Component.LEFT_ALIGNMENT);
						eventListPanel.add(card);
					}
					catch (Exception ex)
					{
						log.warn("Error building event card for '{}'", event.title, ex);
					}
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
			eventListPanel.add(buildEmptyState("Loading events...", null));
			eventListPanel.revalidate();
			eventListPanel.repaint();
		});
	}

	public void showConfigRequired()
	{
		SwingUtilities.invokeLater(() ->
		{
			eventListPanel.removeAll();
			eventListPanel.add(buildEmptyState(
				"Configuration required.",
				"Add your JSONBin credentials in Plugin Settings."));
			eventListPanel.revalidate();
			eventListPanel.repaint();
		});
	}

	public void showError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setForeground(ERROR_COLOR);
			statusLabel.setText(message);
		});
	}

	private void showCreateForm()
	{
		createPanel = new EventCreatePanel(
			plugin.getCurrentUsername(),
			event ->
			{
				createPanel.setStatus("Saving...", false);
				plugin.createEvent(event,
					() -> SwingUtilities.invokeLater(this::showList),
					() -> createPanel.setStatus("Failed to save — check your connection.", true));
			},
			this::showList
		);

		// Iterate over snapshot array — avoids relying on component index ordering
		for (Component c : cardContainer.getComponents())
		{
			if (c != listView)
			{
				cardContainer.remove(c);
			}
		}
		cardContainer.add(createPanel, CARD_CREATE);
		cardLayout.show(cardContainer, CARD_CREATE);
	}

	private void showList()
	{
		cardLayout.show(cardContainer, CARD_LIST);
	}

	// Reusable centered state view (empty, loading, config-required)
	private static JPanel buildEmptyState(String primary, String secondary)
	{
		JPanel wrapper = new JPanel(new GridBagLayout()); // GridBagLayout centers child within available space
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JPanel inner = new JPanel();
		inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
		inner.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel primaryLbl = new JLabel(primary);
		primaryLbl.setFont(FontManager.getRunescapeSmallFont());
		primaryLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		primaryLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
		inner.add(primaryLbl);

		if (secondary != null)
		{
			inner.add(Box.createVerticalStrut(4));
			JLabel secondaryLbl = new JLabel(secondary);
			secondaryLbl.setFont(FontManager.getRunescapeSmallFont());
			secondaryLbl.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			secondaryLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
			inner.add(secondaryLbl);
		}

		wrapper.add(inner);
		return wrapper;
	}
}
