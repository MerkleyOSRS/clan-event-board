package com.clanevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

@Slf4j
public class EventCardPanel extends JPanel
{
	private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("EEE d MMM  ·  h:mm a z");

	private static final Color TEAL        = new Color(0x1ABC9C);
	private static final Color TEAL_HOVER  = new Color(0x16A085);
	private static final Color RED         = new Color(0xC0392B);
	private static final Color RED_HOVER   = new Color(0xA93226);
	private static final Color MUTED       = new Color(0x4A4A4A);
	private static final Color MUTED_HOVER = new Color(0x5E5E5E);
	private static final Color CARD_BG     = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color CARD_BORDER = new Color(0x434343);
	private static final Color SEPARATOR   = new Color(0x343434);

	// Collapsed outer panel height (px). Includes the 4 px top margin from EmptyBorder.
	// Enforces a uniform row height across all events regardless of title length.
	private static final int COLLAPSED_H = 62;

	private final JPanel expandedSection;
	private final JLabel chevronLbl;
	private boolean      expanded = false;

	EventCardPanel(ClanEvent event, String currentUsername, boolean canDelete,
		Consumer<ClanEvent> onRsvp, Consumer<ClanEvent> onDelete)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 0, 0, 0));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, COLLAPSED_H)); // fixed size when collapsed

		// ── Card shell ────────────────────────────────────────────────────────
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(CARD_BG);
		// 1px outline defines card bounds; subtle but clear against the list background
		card.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));

		// Teal left accent stripe
		JPanel stripe = new JPanel();
		stripe.setBackground(TEAL);
		stripe.setPreferredSize(new Dimension(3, 0));
		card.add(stripe, BorderLayout.WEST);

		JPanel body = new JPanel(new BorderLayout());
		body.setBackground(CARD_BG);
		body.setBorder(new EmptyBorder(6, 8, 6, 8));
		card.add(body, BorderLayout.CENTER);

		// ── Collapsed header — 2 rows, all info visible at a glance ──────────
		JPanel headerRows = new JPanel();
		headerRows.setLayout(new BoxLayout(headerRows, BoxLayout.Y_AXIS));
		headerRows.setBackground(CARD_BG);
		headerRows.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		ZonedDateTime zdt = ZonedDateTime.ofInstant(
			Instant.ofEpochSecond(event.timestampUtc), ZoneId.systemDefault());

		// Row 1 — title (left)  +  [badge]  [▶] (right)
		JPanel titleRow = new JPanel(new BorderLayout(4, 0));
		titleRow.setBackground(CARD_BG);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel titleLbl = new JLabel(event.title);
		titleLbl.setFont(FontManager.getRunescapeBoldFont());
		titleLbl.setForeground(Color.WHITE);
		titleLbl.putClientProperty("html.disable", Boolean.TRUE);
		titleRow.add(titleLbl, BorderLayout.CENTER);

		// Badge + chevron sit together so the right edge is always occupied
		JPanel rightControls = new JPanel();
		rightControls.setLayout(new BoxLayout(rightControls, BoxLayout.X_AXIS));
		rightControls.setBackground(CARD_BG);
		if (!event.rsvps.isEmpty())
		{
			rightControls.add(makeBadge(String.valueOf(event.rsvps.size())));
			rightControls.add(Box.createHorizontalStrut(5));
		}
		chevronLbl = new JLabel("▶"); // ▶
		chevronLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
		chevronLbl.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		rightControls.add(chevronLbl);
		titleRow.add(rightControls, BorderLayout.EAST);

		headerRows.add(titleRow);
		headerRows.add(Box.createVerticalStrut(4));

		// Row 2 — date · time (left)  +  by host (right, dimmer)
		JPanel dateRow = new JPanel(new BorderLayout(8, 0));
		dateRow.setBackground(CARD_BG);
		dateRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel dtLbl = new JLabel(DT_FMT.format(zdt));
		dtLbl.setFont(FontManager.getRunescapeSmallFont());
		dtLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		dateRow.add(dtLbl, BorderLayout.CENTER);

		JLabel hostLbl = new JLabel("by " + event.host);
		hostLbl.setFont(FontManager.getRunescapeSmallFont());
		hostLbl.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		hostLbl.putClientProperty("html.disable", Boolean.TRUE);
		dateRow.add(hostLbl, BorderLayout.EAST);

		headerRows.add(dateRow);

		headerRows.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleExpanded();
			}
		});
		body.add(headerRows, BorderLayout.NORTH);

		// ── Expanded section ──────────────────────────────────────────────────
		expandedSection = new JPanel();
		expandedSection.setLayout(new BoxLayout(expandedSection, BoxLayout.Y_AXIS));
		expandedSection.setBackground(CARD_BG);
		expandedSection.setBorder(new EmptyBorder(10, 0, 0, 0));
		expandedSection.setVisible(false);

		JSeparator sep = new JSeparator();
		sep.setForeground(SEPARATOR);
		sep.setBackground(CARD_BG);
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		expandedSection.add(sep);
		expandedSection.add(Box.createVerticalStrut(8));

		String attendHeader = event.rsvps.isEmpty()
			? "No attendees yet"
			: "Attending (" + event.rsvps.size() + ")";
		JLabel attendTitle = new JLabel(attendHeader);
		attendTitle.setFont(FontManager.getRunescapeSmallFont());
		attendTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		attendTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		expandedSection.add(attendTitle);

		if (!event.rsvps.isEmpty())
		{
			expandedSection.add(Box.createVerticalStrut(4));
			for (String name : event.rsvps)
			{
				JLabel nameLbl = new JLabel("• " + name); // •
				nameLbl.setFont(FontManager.getRunescapeSmallFont());
				nameLbl.setForeground(Color.WHITE);
				nameLbl.putClientProperty("html.disable", Boolean.TRUE);
				nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
				expandedSection.add(nameLbl);
			}
		}
		expandedSection.add(Box.createVerticalStrut(10));

		boolean alreadyRsvpd = currentUsername != null && event.rsvps.contains(currentUsername);
		JButton rsvpBtn = makeButton(
			alreadyRsvpd ? "Remove RSVP" : "RSVP",
			alreadyRsvpd ? MUTED : TEAL,
			alreadyRsvpd ? MUTED_HOVER : TEAL_HOVER
		);
		rsvpBtn.addActionListener(e -> onRsvp.accept(event));
		expandedSection.add(rsvpBtn);

		if (canDelete)
		{
			expandedSection.add(Box.createVerticalStrut(4));
			JButton deleteBtn = makeButton("Delete Event", RED, RED_HOVER);
			deleteBtn.addActionListener(e -> onDelete.accept(event));
			expandedSection.add(deleteBtn);
		}

		body.add(expandedSection, BorderLayout.CENTER);
		add(card, BorderLayout.CENTER);
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		expandedSection.setVisible(expanded);
		chevronLbl.setText(expanded ? "▼" : "▶"); // ▼ : ▶
		// Lift the max-height constraint while expanded so the card can grow freely
		setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? Short.MAX_VALUE : COLLAPSED_H));
		revalidate();
		repaint();
		Container p = getParent();
		if (p != null)
		{
			p.revalidate();
			p.repaint();
		}
	}

	private static JLabel makeBadge(String text)
	{
		JLabel badge = new JLabel(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		badge.setBackground(TEAL);
		badge.setForeground(Color.WHITE);
		badge.setOpaque(false);
		badge.setFont(FontManager.getRunescapeSmallFont());
		badge.setBorder(new EmptyBorder(2, 6, 2, 6));
		badge.setToolTipText("RSVPs");
		return badge;
	}

	private static JButton makeButton(String label, Color bg, Color hoverBg)
	{
		JButton btn = new JButton(label)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		btn.setBackground(bg);
		btn.setForeground(Color.WHITE);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setOpaque(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { btn.setBackground(hoverBg); }

			@Override
			public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
		});
		return btn;
	}
}
