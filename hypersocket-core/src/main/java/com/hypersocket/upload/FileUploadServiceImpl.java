package com.hypersocket.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.UUID;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hypersocket.events.EventService;
import com.hypersocket.i18n.I18NService;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.permissions.PermissionService;
import com.hypersocket.permissions.PermissionType;
import com.hypersocket.realm.Realm;
import com.hypersocket.resource.AbstractResourceRepository;
import com.hypersocket.resource.AbstractResourceServiceImpl;
import com.hypersocket.resource.ResourceChangeException;
import com.hypersocket.resource.ResourceCreationException;
import com.hypersocket.resource.ResourceNotFoundException;

@Service
public class FileUploadServiceImpl extends
		AbstractResourceServiceImpl<FileUpload> implements FileUploadService {

	public static final String RESOURCE_BUNDLE = "FileUploadService";
	public static final String UPLOAD_PATH = "conf/uploads/";
	
	static MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();

	static Logger log = LoggerFactory.getLogger(FileUploadServiceImpl.class);
	
	public static final String CONTENT_INPUTSTREAM = "ContentInputStream";
	
	@Autowired
	FileUploadRepository repository;

	@Autowired
	I18NService i18nService;

	@Autowired
	PermissionService permissionService;

	@Autowired
	EventService eventService;

	public FileUploadServiceImpl() {
		super("fileUploads");
	}
	
	@PostConstruct
	private void postConstruct() {

		i18nService.registerBundle(RESOURCE_BUNDLE);
		setAssertPermissions(false);
	}
	
	@Override
	public String getContentType(String uuid) throws ResourceNotFoundException, IOException {
		return mimeTypesMap.getContentType(getFile(uuid));
	}

	@Override
	public FileUpload createFile(final MultipartFile file, final Realm realm, String type)
			throws ResourceCreationException, AccessDeniedException,
			IOException {

		return createFile(file, realm, true, type, new DefaultFileUploadStore(realm));
	}

	@Override
	public FileUpload createFile(MultipartFile file, Realm realm,
			boolean persist, String type, FileUploadStore uploadStore)
			throws ResourceCreationException, AccessDeniedException,
			IOException {

		return createFile(file.getInputStream(), file.getOriginalFilename(),
				realm, persist, type, uploadStore);
	}

	@Override
	public FileUpload createFile(InputStream in, String filename, Realm realm,
			boolean persist, String type, FileUploadStore uploadStore)
			throws ResourceCreationException, AccessDeniedException,
			IOException {

		String uuid = UUID.randomUUID().toString();
		FileUpload fileUpload = new FileUpload();
		fileUpload.setFileName(filename);
		fileUpload.setRealm(realm);
		fileUpload.setName(uuid);
		fileUpload.setType(type);

		try {

			MessageDigest md5 = MessageDigest.getInstance("MD5");
			DigestInputStream din = null;
			OutputStream out = null;

			try {

				din = new DigestInputStream(in, md5);

				fileUpload.setFileSize(uploadStore.writeFile(uuid, din));

				String md5String = Hex.encodeHexString(md5.digest());
				fileUpload.setMd5Sum(md5String);
			} finally {
				IOUtils.closeQuietly(out);
				IOUtils.closeQuietly(din);
				IOUtils.closeQuietly(in);
			}

			if (persist) {
				createResource(fileUpload, new HashMap<String, String>());
			}

			return fileUpload;
		} catch (Throwable e) {
			fireResourceCreationEvent(fileUpload, e);
			throw new ResourceCreationException(
					FileUploadServiceImpl.RESOURCE_BUNDLE,
					"error.genericError", e.getMessage());
		}

	}

	@Override
	public FileUpload getFileByUuid(String uuid) throws ResourceNotFoundException {
		FileUpload upload = repository.getFileByUuid(uuid);
		if(upload==null) {
			throw new ResourceNotFoundException(RESOURCE_BUNDLE, "error.fileNotFound", uuid);
		}
		return upload;
	}

	@Override
	public void deleteFile(FileUpload fileUpload)
			throws ResourceChangeException, AccessDeniedException {

		try {
			File file = new File(UPLOAD_PATH + fileUpload.getRealm().getId()
					+ "/" + fileUpload.getName());
			if (file.delete()) {
				deleteResource(fileUpload);
			} else {
				throw new ResourceChangeException(RESOURCE_BUNDLE,
						"error.noValidatorPresent");
			}
		} catch (Throwable e) {
			fireResourceDeletionEvent(fileUpload, e);
			throw e;
		}

	}

	@Override
	public void downloadURIFile(String uuid, HttpServletRequest request, HttpServletResponse response, boolean forceDownload)
			throws IOException, AccessDeniedException, ResourceNotFoundException {

		FileUpload fileUpload = getFileByUuid(uuid);

		File file = getFile(uuid);
		
		InputStream in = new FileInputStream(file);


			String contentType = mimeTypesMap.getContentType(fileUpload.getFileName());
			response.setContentType(contentType);

			if(forceDownload) {
				response.setHeader("Content-disposition", "attachment; filename="
					+ fileUpload.getFileName());
			}

			if (file.length() < 1024000) {
				try  {
					IOUtils.copy(in,  response.getOutputStream());
					response.flushBuffer();
				} catch(IOException ex) {
					log.error("Failed to copy file", ex);
				} finally {
					in.close();
				}
			} else {
				// Let the HTTP server handle it.
				request.setAttribute(CONTENT_INPUTSTREAM, in);
			}
	}
	
	@Override
	public File getFile(String uuid) throws IOException, ResourceNotFoundException {
		FileUpload fileUpload = getFileByUuid(uuid);

		File file = new File(UPLOAD_PATH + "/" + fileUpload.getRealm().getId()
				+ "/" + fileUpload.getName());
		
		return file;
	}

	@Override
	protected AbstractResourceRepository<FileUpload> getRepository() {
		return repository;
	}

	@Override
	protected String getResourceBundle() {
		return RESOURCE_BUNDLE;
	}

	@Override
	public Class<? extends PermissionType> getPermissionType() {
		return FileUploadPermission.class;
	}
	
	protected Class<FileUpload> getResourceClass() {
		return FileUpload.class;
	}
	
	@Override
	protected void fireResourceCreationEvent(FileUpload resource) {
	}

	@Override
	protected void fireResourceCreationEvent(FileUpload resource, Throwable t) {
	}

	@Override
	protected void fireResourceUpdateEvent(FileUpload resource) {
	}

	@Override
	protected void fireResourceUpdateEvent(FileUpload resource, Throwable t) {
	}

	@Override
	protected void fireResourceDeletionEvent(FileUpload resource) {
	}

	@Override
	protected void fireResourceDeletionEvent(FileUpload resource, Throwable t) {
	}

	@Override
	public FileUpload createFile(File outputFile, String filename,
			Realm realm, boolean persist, String type) throws ResourceCreationException, AccessDeniedException, IOException {
		
		InputStream in = new FileInputStream(outputFile);
		try {
			return createFile(in, filename, realm, persist, type, new DefaultFileUploadStore(realm));
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	class DefaultFileUploadStore implements FileUploadStore {
		
		Realm realm;
		DefaultFileUploadStore(Realm realm) {
			this.realm = realm;
		}
		
		public long writeFile(String uuid, InputStream in)
				throws IOException {

			File f = new File("conf/uploads/" + realm.getId() + "/" + uuid);
			f.getParentFile().mkdirs();
			f.createNewFile();

			OutputStream out = new FileOutputStream(UPLOAD_PATH
					+ realm.getId() + "/" + uuid);

			try {
				IOUtils.copyLarge(in, out);
			} finally {
				IOUtils.closeQuietly(out);
				IOUtils.closeQuietly(in);
			}

			return f.length();
		}
	}

	@Override
	public String getContentType(File file) {
		return mimeTypesMap.getContentType(file);
	}
}
