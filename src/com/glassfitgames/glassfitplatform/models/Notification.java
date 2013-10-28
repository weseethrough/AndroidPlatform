package com.glassfitgames.glassfitplatform.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.roscopeco.ormdroid.Column;
import com.roscopeco.ormdroid.Entity;

/**
 * A notification to be displayed to the user.
 * 
 * Consistency model: Client can mark notifications as read
 *                    Server can upsert using server id.
 */
public class Notification extends Entity {

	@JsonProperty("_id")
	@JsonRawValue
	@Column(unique = true)
	public String id;
	public boolean read = false;
	@JsonRawValue
	public String message;

	@JsonIgnore
	public boolean dirty = false;
	
	public Notification() {
	}
	
	public static List<Notification> getNotifications() {
		return query(Notification.class).executeMulti();
	}

	public void setGuid(JsonNode node) {
		this.id = node.toString();
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		if (this.read != read) dirty = true;
		this.read = read;
	}

	public String getMessage() {
		return message;
	}

	public void flush() {
		if (dirty) {
			dirty = false;
			save();
		}
	}
	
}
