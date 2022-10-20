package tsurumai.workflow.vtime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

//import org.glassfish.jersey.jackson.JacksonFeature;
//import org.glassfish.jersey.server.ResourceConfig;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import tsurumai.workflow.util.ServiceLogger;

//@ApplicationPath("/api/vtime/*")
@Path("/vtime")
@WebListener
public class WorldService /*extends ResourceConfig*/ /*extends Application*/ implements ServletContextListener{
	
	//static Hashtable<String, World> worlds = new Hashtable<>();
	ServiceLogger logger = ServiceLogger.getLogger();
	
//	static String[] DEFAULT_WORLDS=new String[] {"world1", "world2"};
	/*
	public WorldService() {//明示的に指定しないとjerseyがjacksonを使ってくれない。なぜだろう。
		packages("tsurumai.workflow.vtime");
		register(JacksonFeature.class);
	}
	*/
	/**指定された名前のワールドを返す。
	 * 存在しない場合は新規に作成する
	 * */
	World resolveWorld(String name) {
		return World.resolve(name);
	}
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		ServletContextListener.super.contextInitialized(sce);
		logger.info(String.format("context initialized. context=%s to %s", sce.getServletContext().getContextPath(), sce.getServletContext().getRealPath("")));
		String str = sce.getServletContext().getContextPath();
//		for (String n : DEFAULT_WORLDS) {
//			worlds.put(n, new World(n));
//		}
	}
	
	/**ワールドデータを取り出す*/
	@Path("/{world}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@JsonSerialize(using = JsonSerializer.class)
	public World get(@PathParam("world") String world) {
		logger.enter(world);
		World w =  World.resolve(world);
		return w;
	}
	
	/**ワールドデータをロードする*/
	@Path("/{world}")
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public void load(@PathParam("world") String world, World data) {
		logger.enter(world, data);
		World.load(data);
	}

	
	
	/**すべてのワールドデータを取り出す*/
	@Path("/worlds")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public World[] getAll() {
		logger.enter();
		World[] ret = World.getWorlds();
		return ret;
	}
	
	/**すべてのワールドデータをロードする*/
	@Path("/worlds")
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public void loadAll(World[] data) {
		logger.enter(data);
		World.loadWorlds(data);
	}

	
	
	/**ワールドを初期化する
	 * allならすべてのワールドを初期化
	 * */
	@Path("/{world}/init")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	@POST
	public World[] initialize(@PathParam("world") String world, @FormParam("base") Date base, @FormParam("timescale") double timescale, @Context HttpServletRequest req) {
		
		logger.enter(world, base, timescale);

		Collection<String> target = new ArrayList<>();

		
		if("all".equals(world)) {
			return World.initializeAll(base, timescale);
		}else {
			return new World[] {World.resolve(world).initialize(base, timescale)};
		}
	}
	
	/**ワールドの時刻を返す*/
	@Path("/{world}/time")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Date getTime(@PathParam("world") String world) throws IOException{
		logger.enter(world);
		return resolveWorld(world).getTime();
	}
	
	/**ワールドの時間進行を停止する*/
	@Path("/{world}/pause")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public World[] pause(@PathParam("world") String world) {
		logger.enter(world);
		if("all".equals(world)){
			for(World w : World.getWorlds())
				w.resume();
		}else {
			resolveWorld(world).pause();
		}
		return this.getAll();
	}
	
	/**ワールドの時間進行を再開する*/
	@Path("/{world}/resume")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public World[] resume(@PathParam("world") String world) {
		logger.enter(world);
		if("all".equals(world)){
			for(World w : World.getWorlds())
				w.resume();
		}else {
			resolveWorld(world).resume();
		}
		return this.getAll();
	}

	@Path("/{world}/scheduler")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Scheduler getScheduler(@PathParam("world") String world) {
		logger.enter(world);
		return resolveWorld(world).getScheduler();
	}
	
//	/**ワールドを初期化する
//	 * */
//	@Path("/all/init")
//	@POST
//	@Consumes(MediaType.APPLICATION_JSON)
//	@Produces(MediaType.APPLICATION_JSON)
//	public void initializeAll(@PathParam("world") String world, @QueryParam("base") Date base) {
//		Util.enter(world, base);
//		
//		if(this.worlds.isEmpty())
//			Arrays.asList(DEFAULT_WORLDS).forEach(e->{this.resolveWorld(e);});
//		this.worlds.values().forEach(e -> {resolveWorld(e.name).initialize(base);});
//	}

//	/**ワールドの時間進行を停止する*/
//	@Path("/all/pause")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public void pauseAll() {
//		logger.enter();
//		for(World w : World.getWorlds())
//			w.pause();
//	}
//	
//	/**ワールドの時間進行を再開する*/
//	@Path("/all/resume")
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	public void resumeAll(@PathParam("world") String world) {
//		logger.enter(world);
//		for(World w : World.getWorlds())
//			w.resume();
//	}
	
}
