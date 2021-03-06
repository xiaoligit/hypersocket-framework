package com.hypersocket.client.gui.jfx;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class Popup extends Stage {

	private boolean sizeObtained;
	private double position;
	private PositionType positionType;

	public enum PositionType {
		POSITIONED, DOCKED
	}

	public Popup(Window parent, Scene scene) {
		this(parent, scene, true);
	}

	public Popup(Window parent, Scene scene, boolean dismissOnFocusLost) {
		this(parent, scene, dismissOnFocusLost, PositionType.DOCKED);
	}

	public Popup(Window parent, Scene scene, boolean dismissOnFocusLost,
			PositionType positionType) {
		super(
				Platform.isSupported(ConditionalFeature.TRANSPARENT_WINDOW) ? StageStyle.TRANSPARENT
						: StageStyle.UNDECORATED);

		this.positionType = positionType;

		initOwner(parent);
		setScene(scene);
		setMinHeight(24);

		// This is to align the window AFTER we know it's size.
		ChangeListener<? super Number> l = new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable,
					Number oldValue, Number newValue) {
				if (!sizeObtained) {
					sizeToScene();
					sizeObtained = true;
				}
			}
		};
		widthProperty().addListener(l);

		if (dismissOnFocusLost) {
			focusedProperty().addListener(new ChangeListener<Boolean>() {

				@Override
				public void changed(
						ObservableValue<? extends Boolean> observable,
						Boolean oldValue, Boolean newValue) {
					/* NOTE - This may look crazy, but this seems to work-around
					 * a bug in JavaFX / Mac OS X (1.8.0_51) that occurs when trying to 
					 * hide a stage whilst processing a focus event. By placing both
					 * operations on the event queue the problem goes away
					 */
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							if (oldValue && !newValue && !isChildFocussed()) {
								hide();
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										if (!parent.focusedProperty().get()
												&& Configuration.getDefault()
														.autoHideProperty()
														.get()) {
											hideParent(parent);
										}
									}
								});
							}
						}
					});
				}
			});
		}

		// Watch for all changes and reposition this popup if appropriate
		ChangeListener<Boolean> cl = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable,
					Boolean oldValue, Boolean newValue) {
				sizeToScene();
			}
		};
		Configuration cfg = Configuration.getDefault();
		cfg.topProperty().addListener(cl);
		cfg.bottomProperty().addListener(cl);
		cfg.leftProperty().addListener(cl);
		cfg.rightProperty().addListener(cl);
		cfg.avoidReservedProperty().addListener(cl);
		ChangeListener<Number> sl = new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable,
					Number oldValue, Number newValue) {
				sizeToScene();
			}
		};
		cfg.sizeProperty().addListener(sl);
		cfg.monitorProperty().addListener(sl);

		Property<Color> colorProperty = cfg.colorProperty();
		colorProperty.addListener(new ChangeListener<Color>() {
			@Override
			public void changed(ObservableValue<? extends Color> observable,
					Color oldValue, Color newValue) {
				Client.setColors(scene);
			}
		});
		Client.setColors(scene);

		sizeToScene();
	}

	@Override
	public void hide() {
		super.hide();
		Client.setColors(getScene());
	}

	public void setPosition(double position) {
		this.position = position;
	}

	public double getPosition() {
		return position;
	}

	public PositionType getPositionType() {
		return positionType;
	}

	public void setPositionType(PositionType positionType) {
		this.positionType = positionType;
	}

	public void popupAndWait() {
		if (!isShowing()) {
			sizeToScene();
			showAndWait();
		}
	}

	public void popup() {
		if (!isShowing()) {
			sizeToScene();

			Timeline t = new Timeline(new KeyFrame(
					Duration.millis(Dock.AUTOHIDE_DURATION * 3), new KeyValue(
							new WritableValue<Number>() {
								@Override
								public Number getValue() {
									Color c = (Color) getScene().fillProperty()
											.getValue();
									return c.getOpacity();
								}

								@Override
								public void setValue(Number value) {
									Color c = (Color) getScene().fillProperty()
											.getValue();
									getScene().fillProperty().set(
											new Color(c.getRed(), c.getGreen(),
													c.getBlue(), value
															.doubleValue()));

								}
							}, 1)));

			/*
			 * Absolute no idea why this runLater() is required, but without it
			 * sizeToScene calculates an incorrect height.
			 */
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					show();
					t.playFromStart();
				}
			});
		}
	}

	public void sizeToScene() {
		super.sizeToScene();
		Configuration cfg = Configuration.getDefault();
		if (cfg.topProperty().get()) {
			setY(getOwner().getY() + getOwner().getHeight());
			switch (positionType) {
			case POSITIONED:
				if (position + getWidth() > getOwner().getWidth() - getWidth())
					setX(getOwner().getWidth() - getWidth());
				else
					setX(position);
				break;
			default:
				setX(getOwner().getX() + getOwner().getWidth() - getWidth());
			}
		} else if (cfg.bottomProperty().get()) {
			setY(getOwner().getY() - getHeight());
			switch (positionType) {
			case POSITIONED:
				if (position + getWidth() > getOwner().getWidth() - getWidth())
					setX(getOwner().getWidth() - getWidth());
				else
					setX(position);
				break;
			default:
				setX(getOwner().getX() + getOwner().getWidth() - getWidth());
			}
		} else if (cfg.leftProperty().get()) {
			setX(getOwner().getX() + getOwner().getWidth());
			switch (positionType) {
			case POSITIONED:
				if (position + getHeight() > getOwner().getHeight()
						- getHeight())
					setY(getOwner().getHeight() - getHeight());
				else
					setY(position);
				break;
			default:
				setY(getOwner().getY());
			}
		} else if (cfg.rightProperty().get()) {
			setX(getOwner().getX() - getWidth());
			switch (positionType) {
			case POSITIONED:
				if (position + getHeight() > getOwner().getHeight()
						- getHeight())
					setY(getOwner().getHeight() - getHeight());
				else
					setY(position);
				break;
			default:
				setY(getOwner().getY());
			}
		}
	}

	protected void hideParent(Window parent) {
		((Stage) parent).setIconified(true);
	}

	protected boolean isChildFocussed() {
		return false;
	}
}